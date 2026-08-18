package com.tskforging.mtsandroid;

public final class MaterialParser {
    public static final class Doc {
        public String raw="",order="",materialItem="",detail="",partNo="",partName="",weight="",weightUnit="",qty="",qtyUnit="",lot="",charge="";
        public boolean valid(){return !materialItem.isEmpty()&&!detail.isEmpty();}
    }
    private MaterialParser(){}
    public static Doc order(String raw){String[] f=fields(raw);Doc d=new Doc();d.raw=raw;d.order=g(f,0);d.materialItem=g(f,1);d.detail=g(f,2);d.partNo=g(f,3);d.partName=g(f,4);d.qty=g(f,5);d.qtyUnit=g(f,6);d.weight=g(f,7);d.weightUnit=g(f,8);return d;}
    public static Doc tag(String raw){String[] f=fields(raw);Doc d=new Doc();d.raw=raw;d.order=g(f,0);d.materialItem=g(f,1);d.detail=g(f,2);d.partNo=g(f,3);d.partName=g(f,4);d.weight=g(f,5);d.weightUnit=g(f,6);d.qty=g(f,7);d.qtyUnit=g(f,8);d.lot=g(f,10);d.charge=g(f,11);return d;}
    public static String norm(String s){return s==null?"":s.trim().replaceAll("\\s+"," ").toUpperCase();}
    private static String[] fields(String raw){return (raw==null?"":raw.trim()).split("\\|",-1);}
    private static String g(String[] f,int i){return i<f.length?f[i].trim():"";}
}
