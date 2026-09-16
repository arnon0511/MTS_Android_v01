import json
import tempfile
import threading
import unittest
import urllib.request
from datetime import datetime
from pathlib import Path

from http.server import ThreadingHTTPServer
from server import App, Database, MACHINES


class ReceiverTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.db = Database(Path(self.temp.name))

    def tearDown(self):
        self.temp.cleanup()

    def event(self, event_id, event_type, payload):
        ts = int(datetime(2026, 9, 16, 10, 0).timestamp() * 1000)
        return {
            "event_id": event_id, "event_type": event_type, "machine_id": "SC23",
            "group_name": "Cutting 2", "plan_group": "Cut Rack Bar",
            "employee": "TEST", "shift_name": "DAY", "event_ms": ts,
            "payload": payload,
        }

    def test_ingest_approve_dashboard_and_export(self):
        plan = Path(__file__).parents[1] / "sample_data" / "Exam_Plan.xlsx"
        result = self.db.import_plan(plan)
        self.assertEqual(result["month"], "2026-09")
        for i, machine in enumerate(MACHINES):
            e=self.event(f"status-{i}", "STATUS", {"status": "PLANNED_STOP", "reason": "NO PLAN", "detail": "test"})
            e["machine_id"]=machine;e["group_name"],e["plan_group"]=MACHINES[machine]
            self.assertTrue(self.db.ingest(e))
        self.assertTrue(self.db.ingest(self.event("2", "TAG", {"this_qty": 100, "item": "FP0001", "part_no": "P1", "lot": "L1"})))
        self.assertTrue(self.db.ingest(self.event("3", "CLOSE_SHIFT", {"total_ok": 100, "total_ng": 0, "working_sec": 3600, "stop_sec": 0})))
        self.assertFalse(self.db.ingest(self.event("3", "CLOSE_SHIFT", {})))
        self.db.approve("2026-09-16", "DAY", "Wichan")
        dash = self.db.dashboard("2026-09")
        rack = next(x for x in dash["groups"] if x["group"] == "Cut Rack Bar")
        self.assertEqual(rack["actual_qty"], 100)
        self.assertAlmostEqual(rack["actual_hours"], 1.0)
        self.assertEqual(dash["daily"][0]["date"], "2026-09-16")
        self.assertEqual(dash["daily"][0]["actual_qty"], 100)
        self.assertTrue(any(x["group"] == "Other" for x in dash["groups"]))
        self.assertTrue(self.db.export_xlsx("2026-09").exists())

    def test_loss_reason_duration(self):
        for i, machine in enumerate(MACHINES):
            e=self.event(f"reported-{i}", "STATUS", {"status": "RUNNING", "reason": "", "detail": ""})
            e["machine_id"]=machine;e["group_name"],e["plan_group"]=MACHINES[machine]
            self.db.ingest(e)
        stop=self.event("stop", "STATUS", {"status":"UNPLANNED_STOP","reason":"Machine Trouble","detail":"Motor"})
        stop["event_ms"] += 60_000
        resume=self.event("resume", "STATUS", {"status":"RUNNING","reason":"","detail":""})
        resume["event_ms"] += 3_660_000
        close=self.event("close", "CLOSE_SHIFT", {"total_ok":0,"total_ng":0,"working_sec":0,"stop_sec":3600})
        close["event_ms"] += 3_700_000
        self.db.ingest(stop);self.db.ingest(resume);self.db.ingest(close)
        self.db.approve("2026-09-16", "DAY", "Supat")
        losses=self.db.dashboard("2026-09")["losses"]
        self.assertAlmostEqual(losses[0]["hours"], 1.0)
        self.assertIn("Motor", losses[0]["reason"])

    def test_unknown_supervisor_rejected(self):
        with self.assertRaises(ValueError):
            self.db.approve("2026-09-16", "DAY", "Unknown")

    def test_http_health_and_event(self):
        app = App(self.db, "127.0.0.1", 0)
        httpd = ThreadingHTTPServer(("127.0.0.1", 0), app.handler())
        thread = threading.Thread(target=httpd.serve_forever, daemon=True); thread.start()
        try:
            base = f"http://127.0.0.1:{httpd.server_address[1]}"
            with urllib.request.urlopen(base + "/api/health") as r:
                self.assertTrue(json.loads(r.read())["ok"])
            raw = json.dumps(self.event("http-1", "STATUS", {"status":"RUNNING","reason":"","detail":""})).encode()
            req = urllib.request.Request(base + "/api/events", data=raw, headers={"Content-Type":"application/json"}, method="POST")
            with urllib.request.urlopen(req) as r:
                self.assertTrue(json.loads(r.read())["accepted"])
        finally:
            httpd.shutdown(); thread.join(timeout=3); httpd.server_close()


if __name__ == "__main__":
    unittest.main()
