"""Behavior tests against browser DOM fixtures, without Instagram credentials."""
from pathlib import Path
from playwright.sync_api import sync_playwright

SCRIPT = Path(__file__).resolve().parents[1].joinpath('app/assets/filter.js').read_text()

def post(user, key, kind='p', header=True):
    return f'<article id="{key}"><header>{f"<a href=\"/{user}/\">{user}</a>" if header else "Unknown"}</header><a href="/{kind}/{key}/">Time</a><div>Content {key}</div><button>Like</button></article>'

def fixture(posts):
    return '<nav><a href="/reels/">Reels</a><a href="/direct/inbox/">Inbox</a></nav><main><aside id="stories"><button>Tu historia</button><a href="/stories/alice/123/">Alice</a></aside><section id="feed">' + posts + '</section><section id="discovery">More recommendations</section></main>'

def install(page, html, config):
    page.route('https://www.instagram.com/**', lambda route: route.fulfill(status=200, content_type='text/html', body=html))
    page.goto('https://www.instagram.com/')
    page.evaluate('(config) => window.CALMA_CONFIG=config', config)
    page.evaluate('(config) => window.__calmaRelations={owner:"1",phase:"ready",following:config.following,friends:config.friends,followingReady:true,friendsReady:true}', config)
    page.evaluate(SCRIPT)

def visible(page, selector):
    return page.locator(selector).is_visible()

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path='/usr/bin/chromium', headless=True, args=['--no-sandbox'])
    page = browser.new_page(viewport={'width':390,'height':844})
    config = {'mode':1,'following':['alice'],'friends':['bob'],'reels':True,'limit':2}
    html = fixture(post('alice','a1') + post('bob','b1') + post('alice','r1','reel') + post('alice','unknown',header=False) + post('alice','a2') + post('alice','a3'))
    install(page, html, config)
    assert visible(page,'#a1') and visible(page,'#a2')
    assert visible(page, '#stories') and visible(page, '#stories button'), 'Preserve Instagram stories and your story button'
    for key in ['b1','r1','unknown','a3','discovery']:
        assert not visible(page,'#'+key), key
    assert not visible(page,'nav a[href="/reels/"]')
    assert page.evaluate('window.__calma.count()') == 2
    assert 'límite de 2' in page.locator('#calma-end').inner_text()
    page.evaluate('(html) => document.querySelector("#feed").insertAdjacentHTML("beforeend",html)', post('alice','a4'))
    page.wait_for_timeout(300)
    assert not visible(page,'#a4'), 'Dynamic posts must obey cap'
    page.evaluate('history.pushState({},"","/bob/");window.__calma.scan()')
    assert visible(page,'#b1') and not page.locator('#calma-end').count(), 'Profiles must remain accessible'
    page.evaluate('history.pushState({},"","/");window.__calma.scan()')
    assert not visible(page,'#b1')
    page.evaluate('''document.querySelector('nav').insertAdjacentHTML('beforeend','<a id="dynamic-reel" href="/reel/dynamic/">Clip</a>')''')
    page.wait_for_function('document.querySelector("#dynamic-reel").classList.contains("calma-hidden")')
    page.evaluate('document.querySelector("#dynamic-reel").href="/alice/"')
    page.wait_for_function('!document.querySelector("#dynamic-reel").classList.contains("calma-hidden")')
    assert visible(page,'#dynamic-reel'), 'A recycled Reel link must reappear when it becomes a profile link'
    page.evaluate('history.pushState({},"","/direct/inbox/")')
    page.evaluate('''window.dmScans=0;window.documentQueries=document.querySelectorAll;
      document.querySelectorAll=function(selector){window.dmScans++;return window.documentQueries.call(this,selector)};
      document.querySelector('main').insertAdjacentHTML('beforeend','<div id="typing">Typing</div>');''')
    page.wait_for_timeout(1800)
    assert page.evaluate('window.dmScans')==0, 'DM mutations must not scan feed links or poll the document'
    page.evaluate('document.querySelectorAll=window.documentQueries;history.back()')
    page.wait_for_function('location.pathname==="/" && document.documentElement.dataset.calmaHome==="true"')
    assert not visible(page,'#b1'), 'Browser Back must synchronously reapply the Home filter'
    page.evaluate('window.__calma.destroy()')
    assert visible(page,'#b1') and visible(page,'nav a[href="/reels/"]')
    config['mode']=2
    page.evaluate('(config) => window.CALMA_CONFIG=config', config)
    page.evaluate(SCRIPT)
    assert visible(page,'#b1') and not visible(page,'#a1'), 'Friends mode must use friends list'
    page.evaluate('window.__calma.destroy()')
    config['friends']=[]
    page.evaluate('window.__calmaRelations.friends=[]')
    page.evaluate('(config) => window.CALMA_CONFIG=config', config)
    page.evaluate(SCRIPT)
    assert not visible(page,'#b1') and 'Sin seguimiento mutuo' in page.locator('#calma-end').inner_text()
    page.evaluate('window.__calma.destroy()')
    page.set_content('<main><form><input type="password"><button>Log in</button></form></main>')
    page.evaluate(SCRIPT)
    assert visible(page,'input[type="password"]') and not page.locator('#calma-end').count()
    page.evaluate('window.__calma.destroy()')
    config.update(mode=0,reels=False,limit=10)
    page.set_content(html)
    page.evaluate('(config) => window.CALMA_CONFIG=config', config)
    page.evaluate(SCRIPT)
    assert visible(page,'#r1') and visible(page,'#b1'), 'Off switches must restore content'
    print('PASS: allowlists, unknown authors, Reels, finite feed, dynamic loading, profiles, empty lists, login and disabling filters.')
    browser.close()
