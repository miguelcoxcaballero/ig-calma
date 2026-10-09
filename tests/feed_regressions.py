"""Feed pagination, recycled DOM, collaborative posts and photo/video carousels."""
from pathlib import Path
from playwright.sync_api import sync_playwright

script=Path(__file__).resolve().parents[1].joinpath('app/assets/filter.js').read_text()
html='''<style>article{height:320px}.spacer{height:1000px}#sentinel{height:24px}</style>
<main><section id="feed">
<article id="collab"><header><a href="/outsider/">Outsider</a><a href="/alice/">Alice</a></header><a href="/p/collab/">Post</a></article>
<article id="prefixed"><a href="/alice/p/prefixed/">Post</a></article>
<article id="div-heading"><div><a href="/outsider/"><img alt="Avatar"></a><a href="/outsider/">Outsider</a><a href="/alice/">Alice</a></div><img alt="Post"><a href="/p/div/">Post</a></article>
<article id="carousel"><header><a href="/alice/">Alice</a></header><a href="/p/carousel/">Post</a><img alt="Photo"><video></video></article>
<article id="recommendation"><header><a href="/outsider/">Outsider</a></header><a href="/p/ad/">Post</a><a href="/alice/">Commenter</a></article>
<article id="duplicate"><header><a href="/alice/">Alice</a></header><a href="/alice/p/prefixed/">Post</a></article>
<article id="video-only"><a href="/alice/"><img alt="Avatar"></a><a href="/p/video/">Post</a><video></video></article>
<div class="spacer" id="virtual-spacer"></div><div id="sentinel"></div>
</section></main>'''
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
    page=browser.new_page(viewport={'width':390,'height':650})
    page.route('https://www.instagram.com/**',lambda r:r.fulfill(content_type='text/html',body=html))
    page.goto('https://www.instagram.com/')
    page.evaluate('window.CALMA_CONFIG={owner:"1",mode:2,reels:true,limit:10};window.__calmaRelations={owner:"1",phase:"ready",friends:["alice"],friendsReady:true}')
    page.evaluate(script)
    for key in ['collab','prefixed','carousel','div-heading']:
        assert page.locator('#'+key).is_visible(),key
    assert not page.locator('#carousel video').is_visible(), 'Reel frame guard remains active for carousel videos'
    assert not page.locator('#recommendation').is_visible(), 'A friend in the comments is not an author'
    assert not page.locator('#video-only').is_visible(), 'A hidden video must not create a blank post or consume the cap'
    assert not page.locator('#duplicate').is_visible(), 'Duplicate permalinks must not repeat a post'
    assert page.evaluate('window.__calma.count()')==4
    for key in ['sentinel','virtual-spacer']:
        assert page.locator('#'+key).evaluate('e=>getComputedStyle(e).display')!='none',key
    assert page.locator('#calma-end').evaluate('e=>e.parentElement.tagName')=='MAIN'
    assert 'Fin de lo cargado' not in page.locator('#calma-end').inner_text()
    page.evaluate('''window.pagesLoaded=0;window.loader=new IntersectionObserver(entries=>{
      if(!entries.some(e=>e.isIntersecting)||window.pagesLoaded)return;
      window.pagesLoaded++;
      document.querySelector('#sentinel').insertAdjacentHTML('beforebegin','<article id="next-page"><header><a href="/alice/">Alice</a></header><a href="/p/next/">Post</a></article>');
    });window.loader.observe(document.querySelector('#sentinel'));window.scrollTo(0,document.body.scrollHeight)''')
    page.wait_for_function('window.pagesLoaded===1')
    page.wait_for_function('document.querySelector("#next-page").classList.contains("calma-approved")')
    assert page.locator('#next-page').is_visible(), 'Scrolling must still load another permitted post'
    # React can reuse the same article without adding/removing any children.
    page.evaluate('document.querySelector("#next-page header a").href="/outsider/"')
    page.wait_for_function('document.querySelector("#next-page").classList.contains("calma-hidden")')
    assert not page.locator('#next-page').is_visible()
    page.evaluate('document.querySelector("#next-page header a").href="/alice/"')
    page.wait_for_function('document.querySelector("#next-page").classList.contains("calma-approved")')
    assert page.evaluate('window.__calma.count()')==5, 'A recycled/restored post must not consume the cap twice'
    print('PASS: scroll-driven second page, virtual spacers/sentinel intact, collaborative and prefixed authors, carousel photos, commenter exclusion, duplicate suppression and recycled author changes.')
    browser.close()
