#!/usr/bin/env python3
"""JVM behavior checks for native Explore grids and main-search filtering."""
from pathlib import Path
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[2]
PACKAGE='es/calma/instagram/nativeapp'
fixtures={
 'com/instagram/user/model/User.java': '''package com.instagram.user.model; public final class User {
   public final String id; public final Boolean follows; public User(String id,Boolean follows){this.id=id;this.follows=follows;} public String getId(){return id;}
 }''',
 'com/instagram/feed/media/Media.java': '''package com.instagram.feed.media; import com.instagram.user.model.User;
 public final class Media { public boolean sponsored; public boolean EKS(){return sponsored;} public final Dictionary A04; public Media(String id,User user,String kind,User... coauthors){ A04=new Dictionary(id,user,kind,java.util.Arrays.asList(coauthors)); }
 public static final class Dictionary { final String id,kind; final User user; final java.util.List<User> coauthors;
   Dictionary(String id,User user,String kind,java.util.List<User> coauthors){this.id=id;this.user=user;this.kind=kind;this.coauthors=coauthors;}
   public User A33(){return user;} public String A7W(){return kind;} public java.util.List<User> A8F(){return coauthors;} public String getId(){return id;}
 }}''',
 'X/_2P5.java': 'package X; public enum _2P5 { FULL_WIDTH, MEDIA_GRID, ONE_BY_TWO_LEFT }',
 'X/_31Y.java': 'package X; public final class _31Y { public _31Y(Boolean autoplay,Double ratio,Integer columns,Integer total){} }',
 'X/_24Z.java': 'package X; public final class _24Z { public final _31Y A00; public final _32B A01; public final _2P5 A02; public _24Z(_31Y info,_32B layout,_2P5 kind){A00=info;A01=layout;A02=kind;} }',
 'X/_32B.java': '''package X; public final class _32B { public Object A01,A02,A03,A04,A05,A06,A07,A08,A09; public java.util.List<?> A0C,A0D,A0E,A0F;
 public _32B(java.util.List<?> media){A0D=media;}
 }''',
 'X/_32D.java': '''package X; import com.instagram.feed.media.Media; public final class _32D {
 public Object A00,A03,A0D; public Media A08,A09;
 public _32D(Object kind,Object clips,Media media,Media ad,Object extended,boolean a,boolean b,boolean c){A00=kind;A03=clips;A08=media;A09=ad;}
 public void A01(){A0D=A08!=null?A08:A09;}
 }''',
 'X/_C9d.java': 'package X; public final class _C9d { public final com.instagram.user.model.User A01; public _C9d(com.instagram.user.model.User user){A01=user;} }',
 'X/_I7G.java': 'package X; public final class _I7G { public final _C9d A00; public _I7G(_C9d row){A00=row;} }',
 'X/_WDs.java': 'package X; public class _WDs { public Object A01,A02,A03,A04; public java.util.List<Object> A0A,A0C; public String A07="next-page",A08="rank"; public boolean A0E=true; }',
 'X/_YCE.java': 'package X; public final class _YCE extends _WDs { public String A00; public java.util.List<Object> A01; }',
 'X/_YCJ.java': 'package X; public final class _YCJ extends _WDs { public java.util.List<Object> A00,A01; }',
 'X/_YCr.java': 'package X; public final class _YCr { public final com.instagram.user.model.User A07; public _YCr(com.instagram.user.model.User user){A07=user;} }',
 PACKAGE+'/CalmaConfig.java': 'package es.calma.instagram.nativeapp; public final class CalmaConfig { static boolean hide=true; public static boolean reels(){return hide;} }',
 PACKAGE+'/NativeRelations.java': '''package es.calma.instagram.nativeapp; import com.instagram.user.model.User;
 public final class NativeRelations {
   static Boolean state(Object session,Object user){if(!(user instanceof User))return null; User u=(User)user; return u.follows!=null?u.follows:((DiscoverTest.Session)session).cache.get(u.id);}
   static boolean known(Object session,Object user,int mode){return session!=null && state(session,user)!=null;}
   static boolean permitted(Object session,Object user,int mode,boolean response){return session!=null && Boolean.TRUE.equals(state(session,user));}
   static Object user(Object session,String id){return session==null?null:new User(id,((DiscoverTest.Session)session).cache.get(id));}
   static boolean isFollowing(Object session,String id){return session!=null && Boolean.TRUE.equals(((DiscoverTest.Session)session).cache.get(id));}
 }''',
 PACKAGE+'/NativeRelationLookup.java': '''package es.calma.instagram.nativeapp; public final class NativeRelationLookup {
   static java.util.List<String> last; static void awaitOffMainThread(Object session,java.util.List<String> ids){last=ids;}
 }''',
 PACKAGE+'/DiscoverTest.java': '''package es.calma.instagram.nativeapp;
 import X.*; import java.util.*; import com.instagram.user.model.User; import com.instagram.feed.media.Media;
 public final class DiscoverTest {
   public static final class Session { final Map<String,Boolean> cache=new HashMap<>(); }
   public static final class Parser { public Object A01; Parser(Object session){A01=session;} }
   public static final class Response { public List<Object> A06; public String A03="cursor",A04="token"; public boolean A09=true; }
   public static final class Search { public List<Object> A00,A01,A02; }
   public static final class Grid { public List<Object> A05; public String A00="reels-cursor",A01="true",A02="next",A03="rank",A04="page"; public boolean A06=true; }
   public static final class Provider { public Object A00; Provider(Object session){A00=session;} }
   static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
   static _32D tile(Media media){return new _32D("MEDIA",null,media,null,null,false,false,false);}
   static _24Z section(_32B layout){return new _24Z(new _31Y(false,1.0,3,3),layout,_2P5.ONE_BY_TWO_LEFT);}
   static Response response(_24Z section){Response r=new Response();r.A06=new ArrayList<>(Arrays.asList(section));return r;}
   static List<?> tiles(Response r){return ((_24Z)r.A06.get(0)).A01.A0D;}
   public static void main(String[] args){
     Session session=new Session(); Parser parser=new Parser(session);
     User followed=new User("1",true), stranger=new User("2",false), unknown=new User("77",null);
     _32D mixed=tile(new Media("followed",followed,"feed")); mixed.A09=new Media("sneak",stranger,"feed"); mixed.A03=new Object();
     _32D outsider=tile(new Media("stranger",stranger,"feed"));
     _32D clip=tile(new Media("clip",followed,"clips"));
     _32D joint=tile(new Media("joint",stranger,"feed",followed));
     _32D unresolved=tile(new Media("unknown",unknown,"feed"));
     _32B layout=new _32B(Arrays.asList(mixed,outsider,clip,joint,unresolved,mixed));
     layout.A0E=Arrays.asList(section(new _32B(Arrays.asList(tile(new Media("nested",followed,"feed"))))));
     _24Z source=section(layout); layout.A09=source; Response result=response(source);
     NativeDiscover.explore(result,parser);
     check(result.A06.size()==1 && ((_24Z)result.A06.get(0)).A02==_2P5.MEDIA_GRID,"rebuilds a valid native grid");
     check(tiles(result).size()==3,"keeps followed, collaboration and nested tiles; removes stranger, unknown, duplicate and Reel");
     _32D safe=(_32D)tiles(result).get(0); check(safe!=mixed && safe.A09==null && safe.A03==null && safe.A0D==mixed.A08,"strips hidden mixed-content siblings");
     check(mixed.A09!=null && layout.A0D.size()==6,"does not mutate native input items");
     check(result.A09 && "cursor".equals(result.A03) && "token".equals(result.A04),"preserves pagination and paging token");
     check(NativeRelationLookup.last.contains("77"),"resolves unknown relationships through bounded native lookup");
     Response empty=response(section(new _32B(Arrays.asList(outsider)))); NativeDiscover.explore(empty,parser);
     check(empty.A06.isEmpty() && empty.A09 && "cursor".equals(empty.A03),"empty filtered page is not false exhaustion");
     Media ad=new Media("followed-ad",followed,"feed");ad.sponsored=true;
     _32D dedicatedAd=new _32D("AD",null,null,new Media("ad-slot",followed,"feed"),null,false,false,false);
     Response ads=response(section(new _32B(Arrays.asList(tile(ad),dedicatedAd))));NativeDiscover.explore(ads,parser);
     check(ads.A06.isEmpty() && ads.A09 && "cursor".equals(ads.A03),"sponsored followed media and dedicated AD slots excluded without ending pagination");
     Grid adSearch=new Grid();adSearch.A05=new ArrayList<>(Arrays.asList(section(new _32B(Arrays.asList(tile(ad),dedicatedAd)))));NativeDiscover.searchGrid(adSearch,parser);
     check(adSearch.A05.isEmpty() && adSearch.A06 && "next".equals(adSearch.A02),"search media ad-only page preserves native continuation");
     CalmaConfig.hide=false; Response reel=response(section(new _32B(Arrays.asList(clip)))); NativeDiscover.explore(reel,parser);
     check(tiles(reel).size()==1,"Reels switch controls clips while authors still must be followed"); CalmaConfig.hide=true;
     _C9d good=new _C9d(followed), bad=new _C9d(stranger), pending=new _C9d(unknown); _I7G wrapped=new _I7G(good);
     Search state=new Search(); state.A00=new ArrayList<>(Arrays.asList(good,bad,pending,"recommendation",wrapped)); state.A01=new ArrayList<>(Arrays.asList("a","b","c","d","e"));
     NativeDiscover.searchOwner(new Provider(session)); NativeDiscover.searchState(state);
     check(state.A00.equals(Arrays.asList(good,wrapped)) && state.A01.equals(Arrays.asList("a","e")),"search metadata stays aligned after filtering cached and wrapped accounts");
     Search rest=new Search();rest.A02=new ArrayList<>(Arrays.asList(good,bad,pending)); NativeDiscover.searchResponse(rest,parser);
     check(rest.A02.equals(Arrays.asList(good)),"REST typeahead only contains verified followed accounts");
     Grid submitted=new Grid();submitted.A05=new ArrayList<>(Arrays.asList(source));NativeDiscover.searchGrid(submitted,parser);
     check(submitted.A05.size()==1 && ((_24Z)submitted.A05.get(0)).A01.A0D.size()==3,"submitted keyword SERP media follows the same verified author rule");
     check(submitted.A06 && "next".equals(submitted.A02) && "rank".equals(submitted.A03) && "reels-cursor".equals(submitted.A00),"SERP media pagination and stream metadata preserved");
     _YCE top=new _YCE();_YCr rawGood=new _YCr(followed);top.A01=new ArrayList<>(Arrays.asList(rawGood,new _YCr(stranger)));
     top.A0A=new ArrayList<>(Arrays.asList(good,bad));top.A0C=new ArrayList<>(Arrays.asList("upsell"));
     ((_WDs)top).A01=new Object();((_WDs)top).A02=new Object();Object inform=new Object();top.A03=inform;
     NativeDiscover.serpEntities(top,parser);
     check(top.A01.equals(Arrays.asList(rawGood)) && top.A0A.equals(Arrays.asList(good)),"top SERP raw and displayed entities both filtered");
     check(((_WDs)top).A01==null && ((_WDs)top).A02==null && top.A03==inform && "next-page".equals(top.A07) && top.A0E,"unverified previews removed without clearing shadowed raw list, native inform module or pagination");
     _YCJ accounts=new _YCJ();accounts.A01=new ArrayList<>(Arrays.asList(followed,stranger));accounts.A0A=new ArrayList<>(Arrays.asList(good,bad));accounts.A00=new ArrayList<>(Arrays.asList("upsell"));accounts.A0C=new ArrayList<>(accounts.A00);
     NativeDiscover.serpEntities(accounts,parser);
     check(accounts.A01.equals(Arrays.asList(followed)) && accounts.A0A.equals(Arrays.asList(good)) && accounts.A00.isEmpty() && accounts.A0C.isEmpty(),"accounts SERP raw users and derived rows remain aligned without foreign previews");
     session.cache.put("77",true); Search cached=new Search();cached.A02=Arrays.asList(pending); NativeDiscover.searchResponse(cached,parser);
     check(cached.A02.size()==1,"account-scoped verified cache permits resolved account");
     Session other=new Session();other.cache.put("77",false);cached.A02=Arrays.asList(pending);NativeDiscover.searchResponse(cached,new Parser(other));
     check(cached.A02.isEmpty(),"account switching cannot reuse another account's relationship");
     Search broken=new Search();broken.A00=Arrays.asList(good);broken.A01=Collections.emptyList();NativeDiscover.searchOwner(new Provider(session));NativeDiscover.searchState(broken);
     check(broken.A00.isEmpty() && broken.A01.isEmpty(),"malformed metadata never corrupts native row pairing");
     Search noOwner=new Search();noOwner.A00=Arrays.asList(good);noOwner.A01=Arrays.asList("a");NativeDiscover.searchState(noOwner);
     check(noOwner.A00.isEmpty(),"search context is consumed and cannot leak to another call");
     System.out.println("Native Discover grid, relationship, Reels, pagination and paired search checks passed");
   }
 }'''
}
with tempfile.TemporaryDirectory(prefix='calma-discover-tests-') as temporary:
 work=Path(temporary)
 for name,source in fixtures.items():
  file=work/'src'/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(source)
 for name in ('NativeDiscover.java','NativeAds.java','StockAccess.java'):
  (work/'src'/PACKAGE/name).write_text((ROOT/'native/src'/PACKAGE/name).read_text())
 classes=work/'classes';classes.mkdir()
 subprocess.run(['java','-jar',str(ROOT/'tools/ecj.jar'),'-1.8','-proc:none','-d',str(classes),*map(str,(work/'src').rglob('*.java'))],check=True)
 names=['_2P5','_31Y','_24Z','_32B','_32D','_C9d','_I7G','_WDs','_YCE','_YCJ','_YCr']
 for file in list(classes.rglob('*.class')):
  data=file.read_bytes()
  for name in names:data=data.replace(('X/'+name).encode(),('X/0'+name[1:]).encode())
  target=Path(str(file).replace('/X/_','/X/0'));target.write_bytes(data)
  if target!=file:file.unlink()
 subprocess.run(['java','-cp',str(classes),'es.calma.instagram.nativeapp.DiscoverTest'],check=True)
