"""Discover author checks, search accounts, route cleanup and rejection behavior."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1]
script=(root/'app/assets/discover.js').read_text()
filters=(root/'app/assets/filter.js').read_text()
html='''<meta charset=utf-8><style>a{display:block;min-height:40px}img{height:30px;width:30px}</style><nav><a href=/direct/inbox/>DM</a></nav><main><input type=search placeholder=Buscar><a id=known href=/alice/p/ABC/>Photo</a><a id=outsider href=/bob/p/BCD/>Photo</a><a id=unknown-friend href=/p/CDE/><img alt=Photo></a><a id=unknown-other href=/p/DEF/><img alt=Photo></a><a id=reel href=/alice/reel/EFG/>Reel</a><a id=friend-profile href=/alice/>Alice</a><a id=other-profile href=/bob/>Bob</a><a href=/explore/tags/trending/><img id=unverified-topic alt=Topic></a><div id=sentinel></div></main>'''
with sync_playwright() as p:
 b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
 context=b.new_context();calls=[];reject=False;release=False;held=[]
 def fulfill(r):
  if reject:r.fulfill(status=429,body='{}');return
  ident=r.request.url.split('/media/')[1].split('/')[0]
  code='CDE' if ident==str(2*64**2+3*64+4) else 'DEF'
  user='alice' if code=='CDE' else 'bob'
  r.fulfill(content_type='application/json',body=json.dumps({'status':'ok','items':[{'code':code,'user':{'username':user}}]}))
 def route(r):
  if '/api/v1/media/' not in r.request.url:r.fulfill(content_type='text/html',body=html);return
  calls.append(r.request.url)
  if not release:held.append(r);return
  fulfill(r)
 context.route('https://www.instagram.com/**',route)
 page=context.new_page();page.goto('https://www.instagram.com/explore/')
 page.evaluate('window.CALMA_CONFIG={owner:"1",mode:0,reels:true,limit:20};window.__calmaRelations={owner:"1",following:["alice"],friends:[],followingReady:true,friendsReady:true}')
 page.evaluate(filters)
 page.evaluate('''window.discoveryMutations=0;const NativeObserver=window.MutationObserver;
 window.MutationObserver=class extends NativeObserver {constructor(callback){super((records,observer)=>{window.discoveryMutations++;callback(records,observer)})}};void 0;''')
 page.evaluate(script)
 assert page.locator('#known').is_visible() and page.locator('#friend-profile').is_visible()
 for key in ['outsider','reel','unknown-friend','unknown-other','other-profile']:assert not page.locator('#'+key).is_visible(),key
 page.evaluate('window.hiddenPauses=0;const video=document.createElement("video");video.pause=()=>window.hiddenPauses++;document.querySelector("#unknown-friend").appendChild(video);video.dispatchEvent(new Event("play",{bubbles:true}))')
 assert page.evaluate('window.hiddenPauses')>0, 'Unverified media must not play in the background'
 release=True
 for r in held:fulfill(r)
 page.wait_for_function('document.querySelector("#unknown-friend").classList.contains("calma-discover-approved")')
 page.wait_for_function('document.querySelector("#unknown-other").classList.contains("calma-discover-rejected")')
 assert len(calls)==2 and page.locator('#unknown-friend').is_visible()
 assert page.locator('#unknown-friend img').is_visible() and not page.locator('#unverified-topic').is_visible(), 'Only verified media is displayed'
 assert page.locator('#sentinel').evaluate('e=>getComputedStyle(e).display')!='none'
 # Recycling a permitted tile must immediately invalidate the old approval.
 page.evaluate('document.querySelector("#known").href="/bob/p/ABC/"')
 page.wait_for_function('document.querySelector("#known").classList.contains("calma-discover-rejected")')
 assert not page.locator('#known').is_visible()
 page.evaluate('window.__calmaRelations.following=[];document.dispatchEvent(new Event("calma-relations"))')
 assert not page.locator('#unknown-friend').is_visible()
 page.evaluate('window.__calmaRelations.following=["alice"];document.dispatchEvent(new Event("calma-relations"))')
 assert page.locator('#unknown-friend').is_visible() and len(calls)==2
 page.evaluate('history.pushState({},"","/bob/")')
 assert page.locator('#outsider').is_visible() and not page.locator('#calma-discover-status').count(), 'Profile navigation restores content'
 page.evaluate('history.pushState({},"","/")')
 page.wait_for_timeout(100)
 page.evaluate('window.discoveryMutations=0;document.querySelector("#known").href="/alice/p/ABC/"')
 page.wait_for_timeout(100)
 assert page.evaluate('window.discoveryMutations')==0, 'Home link recycling must not wake the discovery observer without a search panel'
 # Search dialog works while Home remains the active route.
 page.evaluate('history.pushState({},"","/");document.querySelector("main").insertAdjacentHTML("beforeend",\'<section role="dialog"><input placeholder="Buscar"><a id="search-alice" href="/alice/">Alice</a><a id="search-bob" href="/bob/">Bob</a><a id="search-photo" href="/alice/p/CAB/">Photo</a></section>\')')
 page.wait_for_function('document.querySelector("[role=dialog]").hasAttribute("data-calma-search-scope")')
 assert page.locator('#search-alice').is_visible() and not page.locator('#search-bob').is_visible()
 assert page.locator('#search-photo').is_visible(), 'Verified search photos inside Home main must remain visible'
 page.evaluate('window.closedSearch=document.querySelector("[role=dialog]");window.closedSearch.remove()')
 page.wait_for_timeout(100)
 page.evaluate('window.discoveryMutations=0;document.querySelector("#known").href="/alice/p/ABC/"')
 page.wait_for_timeout(100)
 assert page.evaluate('window.discoveryMutations')==0, 'Closing search must stop observing Home links again'
 assert page.evaluate('!window.closedSearch.querySelector("#search-bob").classList.contains("calma-discover-rejected")'), 'Detached search results must release their filter state'
 page.evaluate('document.body.appendChild(window.closedSearch)')
 page.wait_for_function('document.querySelector("[role=dialog]").hasAttribute("data-calma-search-scope")')
 page.evaluate('history.pushState({},"","/direct/inbox/")')
 assert page.locator('#search-bob').is_visible(), 'DM recipient dialogs must remain unfiltered'
 page.evaluate('''window.discoveryMutations=0;
 for(let i=0;i<100;i++){const message=document.createElement('a');message.href='/alice/';message.textContent='Message';document.querySelector('main').appendChild(message);message.href='/bob/';message.remove()}''')
 page.wait_for_timeout(100)
 assert page.evaluate('window.discoveryMutations')==0, 'Incoming DM messages must do no discovery observer work'
 page.evaluate('document.querySelector("[role=dialog]").remove();history.pushState({},"","/explore/");window.__calmaRelations.owner="2";document.dispatchEvent(new Event("calma-relations"))')
 reject=True
 page.wait_for_function('document.querySelector("#calma-discover-status").textContent.includes("comprobar")')
 n=len(calls);page.wait_for_timeout(1000)
 assert len(calls)==n, 'A rate limit must stop metadata requests, without retries'
 assert not page.locator('#unknown-friend').is_visible(), 'Previous-account metadata cannot reveal another account’s content'
 page.evaluate('window.__calmaDiscover.destroy()')
 assert page.locator('#outsider').is_visible() and not page.locator('#calma-discover-status').count()
 print('PASS: followed-only Discover independent of Home mode, lazy author verification, unknowns hidden, Reels, search profiles/dialogs, recycled tiles, follow/account changes, intact pagination, cleanup and rate-limit stop.')
 b.close()
