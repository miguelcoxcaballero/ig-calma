"""Friends' shared clips are one-item viewers; home cannot show an unchecked frame."""
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[1]
gate=(root/'app/assets/reel-gate.js').read_text()
filters=(root/'app/assets/filter.js').read_text()
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
    page=browser.new_page()
    allowed=[]
    page.on('console',lambda message:allowed.append(message.text.endswith('true')) if message.text.startswith('GATE_RESULT:') else None)
    def route(r):
        path=urlparse(r.request.url).path
        if path.startswith('/reel/'):
            r.abort();return
        r.fulfill(content_type='text/html',body='<main><header><a href="/alice/">Alice</a></header><article data-message-id="m"><a id="clip" href="/reel/clip1/">Shared reel</a></article></main>')
    page.route('https://www.instagram.com/**',route)
    def thread(friend=True):
        page.goto('https://www.instagram.com/direct/t/123/')
        page.evaluate('(friend)=>{window.CALMA_CONFIG={owner:"1",mode:1,limit:20,reels:true,allowedReelPath:""};window.__calmaRelations={owner:"1",friends:friend?["alice"]:[],friendsReady:true,following:["alice"],followingReady:true}}',friend)
        page.evaluate(gate);page.evaluate(filters)
        page.evaluate('window.addEventListener("beforeunload",()=>console.log("GATE_RESULT:"+window.__calmaReelGate.consume("/reel/clip1/")))')
    thread()
    assert page.locator('#clip').is_visible(), 'DM reel links must remain accessible'
    page.locator('#clip').click();page.wait_for_timeout(200)
    assert allowed[-1] is True
    thread(False)
    page.locator('#clip').click();page.wait_for_timeout(200)
    assert allowed[-1] is False, 'Unknown/non-mutual sender must not create permission'
    page.goto('https://www.instagram.com/direct/t/123/')
    page.evaluate('history.replaceState({},"","/reel/clip1/");window.CALMA_CONFIG={owner:"1",mode:1,limit:20,reels:true,allowedReelPath:"/reel/clip1/"};window.__calmaRelations={owner:"1",following:[],friends:[],followingReady:true,friendsReady:true};document.body.innerHTML="<main><article id=one><video id=first controls></video></article><article id=two><video id=next></video></article><button id=play>Play</button></main>"')
    page.evaluate(gate);page.evaluate(filters)
    assert page.evaluate('window.__calmaReelGate.locked()') is True
    assert page.locator('#first').is_visible() and not page.locator('#next').is_visible()
    assert page.evaluate('getComputedStyle(document.body).overflow')=='hidden'
    for event in ['wheel','touchmove']:
        assert page.evaluate('(type)=>{const e=new Event(type,{bubbles:true,cancelable:true});document.querySelector("main").dispatchEvent(e);return e.defaultPrevented}',event)
    assert page.evaluate('const e=new KeyboardEvent("keydown",{key:"ArrowDown",bubbles:true,cancelable:true});document.dispatchEvent(e);e.defaultPrevented')
    assert page.locator('#play').is_visible(), 'Playback controls must remain available'
    # Home's guard runs synchronously with SPA navigation, before an animation frame.
    page.evaluate('history.pushState({},"","/")')
    assert page.locator('html').get_attribute('data-calma-home')=='true'
    assert not page.locator('#first').is_visible(), 'Old reel frame cannot remain visible on Home'
    page.evaluate('document.querySelector("main").innerHTML="<article id=unprocessed><header><a href=/bob/>Bob</a></header><a href=/p/unknown/>Post</a><video></video></article>"')
    assert not page.locator('#unprocessed').is_visible(), 'New unchecked content starts hidden before filter scan'
    page.wait_for_timeout(100)
    assert not page.locator('#unprocessed').is_visible()
    page.evaluate('window.__calmaRelations.following=["alice"];document.querySelector("main").innerHTML="<article id=photo><header><a href=/alice/>Alice</a></header><a href=/p/a/>Photo</a></article>";window.__calma.scan()')
    assert page.locator('#photo').is_visible()
    page.evaluate('document.querySelector("#photo").insertAdjacentHTML("beforeend","<video id=lateclip></video>")')
    assert not page.locator('#lateclip').is_visible(), 'Late video cannot flash inside an approved photo post'
    # The reel gate must be dormant on chats, and react to route changes without polling.
    page.evaluate('window.__calma.destroy();window.__calmaReelGate.destroy();history.replaceState({},"","/direct/t/123/");window.CALMA_CONFIG.allowedReelPath="/reel/clip1/";document.body.innerHTML="<main><article id=one><video id=first></video><textarea id=reply></textarea></article><article id=two><video id=next></video></article></main>"')
    page.evaluate(gate)
    page.evaluate('window.lockWrites=0;window.lockObserver=new MutationObserver(rs=>window.lockWrites+=rs.filter(r=>r.attributeName==="data-calma-reel-locked").length);window.lockObserver.observe(document.documentElement,{attributes:true});for(let i=0;i<100;i++){const message=document.createElement("span");document.querySelector("main").appendChild(message);message.remove()}')
    page.wait_for_timeout(500)
    assert page.evaluate('window.lockWrites')==0, 'Chat mutations must not repeatedly invoke the reel lock'
    assert page.evaluate('const event=new Event("wheel",{bubbles:true,cancelable:true});document.querySelector("main").dispatchEvent(event);!event.defaultPrevented')
    page.evaluate('history.pushState({},"","/reel/clip1/");document.dispatchEvent(new Event("calma-route",{bubbles:true}))')
    assert page.locator('#first').is_visible() and not page.locator('#next').is_visible()
    assert page.evaluate('const event=new KeyboardEvent("keydown",{key:"ArrowDown",bubbles:true,cancelable:true});document.querySelector("#reply").dispatchEvent(event);!event.defaultPrevented'), 'The lock must preserve cursor movement in comments'
    # React can replace the current player, but removing its post cannot expose the next reel.
    page.evaluate('document.querySelector("#first").outerHTML="<video id=first></video>"')
    page.wait_for_timeout(40)
    assert page.locator('#first').is_visible()
    page.evaluate('document.querySelector("#one").remove()')
    page.wait_for_timeout(40)
    assert not page.locator('#next').is_visible(), 'Removing the current post must not promote a recommended reel'
    page.evaluate('window.CALMA_ACTIVE=false;document.dispatchEvent(new Event("calma-visibility"));document.querySelector("main").insertAdjacentHTML("beforeend","<article id=three><video id=later></video></article>")')
    page.wait_for_timeout(40)
    assert not page.locator('#later').is_visible()
    page.evaluate('window.CALMA_ACTIVE=true;document.dispatchEvent(new Event("calma-visibility"));history.pushState({},"","/direct/inbox/");document.dispatchEvent(new Event("calma-route"))')
    assert page.locator('html').get_attribute('data-calma-reel-locked')=='false'
    assert page.evaluate('const event=new Event("touchmove",{bubbles:true,cancelable:true});document.querySelector("main").dispatchEvent(event);!event.defaultPrevented'), 'Leaving the reel must release gesture blockers immediately'
    assert page.locator('.calma-other-reel').count()==0
    print('PASS: friend-only DM authorization, single-clip playback and replacement, no promotion of recommendations, synchronous Home guard, editable controls, dormant chat/background behavior and immediate route cleanup.')
    browser.close()
