"""Theme changes and motion accessibility in the same live document, with original media."""
from pathlib import Path
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[1]
appearance=(root/'app/assets/appearance.js').read_text()
settings=(root/'app/assets/settings.js').read_text()
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
    page=browser.new_page(viewport={'width':390,'height':844})
    page.route('https://www.instagram.com/**',lambda r:r.fulfill(content_type='text/html',body='<style>main{background:rgb(var(--ig-primary-background));color:rgb(var(--ig-primary-text))}</style><main><form><input id="name" value="Miguel"></form><button id="native">Like</button><img id="photo" width="30" height="30" src="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2230%22 height=%2230%22%3E%3Crect fill=%22red%22 width=%2230%22 height=%2230%22/%3E%3C/svg%3E"></main>'))
    page.goto('https://www.instagram.com/accounts/edit/')
    page.evaluate('window.CALMA_CONFIG={mode:1,limit:20,reels:true};window.CALMA_APPEARANCE={dark:false,reduceMotion:false};window.__calmaRelations={phase:"ready",following:[],friends:[]}')
    page.evaluate(appearance);page.evaluate(settings)
    def color(selector,prop='backgroundColor'):
        return page.locator(selector).evaluate('(e,p)=>getComputedStyle(e)[p]',prop)
    assert color('body')=='rgb(255, 255, 255)'
    assert color('main','color')=='rgb(38, 38, 38)'
    src=page.locator('#photo').get_attribute('src')
    page.locator('#name').fill('Do not recreate this document')
    page.evaluate('window.CALMA_APPEARANCE.dark=true;window.__calmaAppearance.refresh()')
    page.wait_for_timeout(200)
    assert color('body')=='rgb(0, 0, 0)' and color('main')=='rgb(0, 0, 0)'
    assert color('main','color')=='rgb(245, 245, 245)'
    assert color('#calma-limit','color')=='rgb(245, 245, 245)'
    assert color('.calma-stepper','borderColor')=='rgb(54, 54, 54)'
    assert page.locator('html').evaluate('e=>getComputedStyle(e).colorScheme')=='dark'
    assert page.locator('#name').input_value()=='Do not recreate this document'
    assert page.locator('#photo').get_attribute('src')==src
    assert color('#photo','filter')=='none', 'Dark mode must not invert photos'
    page.locator('#native').dispatch_event('pointerdown',{'isPrimary':True,'button':0})
    assert page.locator('#native').evaluate('e=>e.classList.contains("calma-pressed")')
    page.locator('#native').dispatch_event('pointerup',{'isPrimary':True,'button':0})
    assert not page.locator('#native').evaluate('e=>e.classList.contains("calma-pressed")')
    page.evaluate('document.activeElement.blur();history.pushState({},"","/direct/inbox/");document.dispatchEvent(new Event("calma-route",{bubbles:true}))')
    page.wait_for_function('document.querySelector("main").getAnimations().length===1')
    assert page.evaluate('document.querySelector("main").getAnimations().length')==1
    assert color('main','transform')=='none', 'Changing tabs must not shift the entire conversation'
    page.evaluate('window.CALMA_APPEARANCE.reduceMotion=true;window.__calmaAppearance.refresh()')
    assert page.evaluate('document.querySelector("main").getAnimations().length')==0
    assert page.locator('#calma-reels').evaluate('e=>getComputedStyle(e,"::before").transitionDuration')=='0s', 'Custom switch thumbs must also respect reduced motion'
    page.evaluate('history.pushState({},"","/");window.__calmaAppearance.refresh()')
    assert page.evaluate('document.querySelector("main").getAnimations().length')==0
    page.evaluate('window.CALMA_APPEARANCE.reduceMotion=false;window.__calmaAppearance.refresh()')
    page.emulate_media(reduced_motion='reduce')
    page.wait_for_timeout(150)
    assert page.locator('html').get_attribute('data-calma-motion')=='reduced'
    page.evaluate('window.CALMA_APPEARANCE.dark=false;window.__calmaAppearance.refresh()')
    assert color('body')=='rgb(255, 255, 255)'
    page.evaluate(appearance)
    assert page.locator('#calma-appearance-style').count()==1
    assert page.evaluate('document.querySelectorAll("body > *").length')==1, 'No wrapper or overlay added'
    # Root theme enforcement should settle instead of repeatedly rewriting its attributes.
    page.evaluate('window.attributeWrites=0;window.testObserver=new MutationObserver(rs=>window.attributeWrites+=rs.length);window.testObserver.observe(document.documentElement,{attributes:true})')
    page.wait_for_timeout(350)
    assert page.evaluate('window.attributeWrites')<10, 'No theme mutation loop'
    page.emulate_media(reduced_motion='no-preference')
    page.evaluate('window.CALMA_APPEARANCE.reduceMotion=false;window.__calmaAppearance.refresh();window.animationCalls=0;const main=document.querySelector("main");main.animate=new Proxy(main.animate,{apply(fn,target,args){window.animationCalls++;return Reflect.apply(fn,target,args)}});for(let i=0;i<100;i++){const item=document.createElement("span");main.appendChild(item);item.remove()}')
    page.wait_for_timeout(600)
    assert page.evaluate('window.animationCalls')==0, 'Incoming messages must not restart whole-page motion'
    page.evaluate('window.CALMA_ACTIVE=false;document.dispatchEvent(new Event("calma-visibility"));history.pushState({},"","/direct/t/123/");document.dispatchEvent(new Event("calma-route"))')
    page.wait_for_timeout(150)
    assert page.evaluate('window.animationCalls')==0, 'Retained background tabs must not animate'
    page.evaluate('window.CALMA_ACTIVE=true;document.dispatchEvent(new Event("calma-visibility"));window.__calmaAppearance.destroy();history.pushState({},"","/direct/inbox/");document.dispatchEvent(new Event("calma-route"))')
    assert page.locator('#calma-appearance-style').count()==0
    print('PASS: live light/dark and reduced-motion changes, media/input preservation, route-event motion without layout shifts, no animation on incoming content or background tabs, cleanup, and no mutation loop.')
    browser.close()
