#!/usr/bin/env python3
"""Exercise native settings insertion against the independently mapped stock model contract.

Fixture class names are renamed after compilation because Redex's leading digit
class names are valid JVM names but not Java identifiers. Stock DEX signatures
and the constructor-free allocation are additionally asserted by SettingsPatch.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = 'es/calma/instagram/nativeapp'
fixtures = {
    'X/_3kU.java': 'package X; public interface _3kU extends java.util.List<Object> {}',
    'X/_0nM.java': '''package X; public final class _0nM {
      public static _3kU A00(Iterable<?> input) {
        java.util.ArrayList<Object> values = new java.util.ArrayList<>();
        for (Object value : input) values.add(value);
        return new Values(values);
      }
      public static final class Values extends java.util.AbstractList<Object> implements _3kU {
        final java.util.List<Object> values;
        Values(java.util.List<Object> values) { this.values=java.util.Collections.unmodifiableList(values); }
        public Object get(int index) { return values.get(index); }
        public int size() { return values.size(); }
      }
    }''',
    'X/_vAE.java': 'package X; public interface _vAE {}',
    'X/_R4P.java': 'package X; public final class _R4P { public final Object A00; public _R4P(Object value) { A00=value; } }',
    'X/_R5u.java': '''package X; public final class _R5u {
      public Object A00; public com.instagram.settings2.core.model.FbtModel A01; public _3kU A02,A03;
    }''',
    'X/_E72.java': 'package X; public final class _E72 { public final _3kU A00; public final boolean A01; public _E72(_3kU sections, boolean loading) { A00=sections; A01=loading; } }',
    'X/_E6t.java': '''package X; public final class _E6t {
      public final _vAE A02; public final _R4P A03; public final com.instagram.settings2.core.model.FbtModel A06;
      public final boolean A0C,A0B,A0A;
      public _E6t(Object badge,Object cell,_vAE id,_R4P destination,com.instagram.settings2.core.model.FbtModel label,
          Object accessibility,Object subtitle,Object detail,Object accessory,Integer icon,boolean visible,boolean disabled,boolean delegated) {
        A02=id; A03=destination; A06=label; A0C=visible; A0B=disabled; A0A=delegated;
      }
    }''',
    'com/instagram/settings2/core/model/FbtModelSource.java': '''package com.instagram.settings2.core.model;
      public interface FbtModelSource { public static final class Literal implements FbtModelSource {
        public final String A00; public Literal(String text) { A00=text; }
      }}''',
    'com/instagram/settings2/core/model/FbtModel.java': '''package com.instagram.settings2.core.model;
      public final class FbtModel { public final FbtModelSource A00; public final X._3kU A01;
        public FbtModel(FbtModelSource source,X._3kU tokens) { A00=source; A01=tokens; }
      }''',
    'android/util/Log.java': 'package android.util; public final class Log { public static int e(String tag,String text,Throwable error) { return 0; } }',
    PACKAGE+'/CalmaConfig.java': 'package es.calma.instagram.nativeapp; public final class CalmaConfig { public static android.content.Context context(){ return null; } }',
    PACKAGE+'/CalmaSettingsActivity.java': 'package es.calma.instagram.nativeapp; public final class CalmaSettingsActivity extends android.app.Activity {}',
    PACKAGE+'/SettingsTest.java': '''package es.calma.instagram.nativeapp;
      import X.*;
      import com.instagram.settings2.core.model.*;
      public final class SettingsTest {
        static void check(boolean ok,String label) { if(!ok) throw new AssertionError(label); }
        public static void main(String[] args) {
          _R5u first = new _R5u(); first.A00="stock-section";
          first.A01=new FbtModel(new FbtModelSource.Literal("Instagram"), null);
          first.A02=_0nM.A00(java.util.Arrays.asList("stock-row")); first.A03=_0nM.A00(java.util.Arrays.asList("footer"));
          Object second=new Object();
          _E72 original=new _E72(_0nM.A00(java.util.Arrays.asList(first,second)),false);
          check(CalmaSettings.content("ACCOUNT_PRIVACY",original)==original,"no insertion into settings subpages");
          _E72 changed=(_E72)CalmaSettings.content("MAIN_SETTINGS_SCREEN",original);
          check(changed!=original,"native main settings receives the row");
          check(changed.A00.size()==2 && changed.A00.get(1)==second,"preserves stock sections and order");
          _R5u inserted=(_R5u)changed.A00.get(0);
          check(inserted!=first && inserted.A00==first.A00 && inserted.A01==first.A01 && inserted.A03==first.A03,"preserves native section identity, title and footers");
          check(first.A02.size()==1 && inserted.A02.size()==2 && inserted.A02.get(1).equals("stock-row"),"no mutation or loss of original rows");
          _E6t row=(_E6t)inserted.A02.get(0);
          check("Tu feed".equals(((FbtModelSource.Literal)row.A06.A00).A00),"native row label");
          check(row.A0C && !row.A0B && !row.A0A,"row visible and clickable");
          check("CALMA_FEED_SETTINGS".equals(row.A02.toString()),"independent unique row identity");
          check(CalmaSettings.content("MAIN_SETTINGS_SCREEN",original)==changed,"recomposition reuses result");
          check(CalmaSettings.content("MAIN_SETTINGS_SCREEN",changed)==changed,"repeated injection cannot duplicate row");
          _E72 empty=new _E72(_0nM.A00(java.util.Collections.emptyList()),true);
          check(CalmaSettings.content("MAIN_SETTINGS_SCREEN",empty)==empty,"loading state remains valid");
          check(CalmaSettings.content("MAIN_SETTINGS_SCREEN",new Object()).getClass()==Object.class,"unknown stock state remains unchanged");
          check(!CalmaSettings.open(new _R4P("https://instagram.com")),"unrelated native navigation untouched");
          check(CalmaSettings.open(row.A03),"own destination consumed before stock coroutine casts");
          System.out.println("Native settings insertion, stock preservation, duplicate and navigation checks passed");
        }
      }''',
}
with tempfile.TemporaryDirectory(prefix='calma-settings-tests-') as temporary:
    work=Path(temporary)
    for name, source in fixtures.items():
        file=work/'src'/name
        file.parent.mkdir(parents=True,exist_ok=True)
        file.write_text(source)
    # In APK builds the DEX patch replaces this factory with the native
    # new-instance / Object.<init> sequence removed by Redex.
    implementation=(ROOT/'native/src'/PACKAGE/'CalmaSettings.java').read_text()
    old='public static Object newSection() { return null; }'
    assert old in implementation
    implementation=implementation.replace(old,'public static Object newSection() { return new X._R5u(); }')
    (work/'src'/PACKAGE/'CalmaSettings.java').write_text(implementation)
    classes=work/'classes'; classes.mkdir()
    android=ROOT/'tools/android-35/android.jar'
    subprocess.run(['java','-jar',str(ROOT/'tools/ecj.jar'),'-1.8','-proc:none','-classpath',str(android),'-d',str(classes),*map(str,(work/'src').rglob('*.java'))],check=True)
    renames=['_3kU','_0nM','_vAE','_R4P','_R5u','_E72','_E6t']
    for file in list(classes.rglob('*.class')):
        data=file.read_bytes()
        for name in renames:
            data=data.replace(('X/'+name).encode(),('X/0'+name[1:]).encode())
        target=Path(str(file).replace('/X/_','/X/0'))
        target.write_bytes(data)
        if target!=file: file.unlink()
    subprocess.run(['java','-cp',str(classes)+':'+str(android),'es.calma.instagram.nativeapp.SettingsTest'],check=True)
