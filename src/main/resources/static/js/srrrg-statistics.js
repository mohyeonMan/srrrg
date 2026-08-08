(function () {
	const app = document.getElementById('statistics-app'); if (!app) return;
	const query = new URLSearchParams(location.search); const projectId = query.get('projectId'); const campaignId = query.get('campaignId'); const code = query.get('code');
	const formatter = new Intl.NumberFormat('ko-KR'); const from = byId('statistics-from'); const to = byId('statistics-to'); const bucket = byId('statistics-bucket');
	setPeriod(30); byId('statistics-filter').addEventListener('submit', (event) => { event.preventDefault(); load(); });
	document.querySelectorAll('[data-days]').forEach(button => button.addEventListener('click', () => { setPeriod(Number(button.dataset.days)); load(); })); load();

	function endpoint() {
		if (campaignId) return `/api/web/campaigns/${encodeURIComponent(campaignId)}/statistics`;
		if (projectId && code) return `/api/web/projects/${encodeURIComponent(projectId)}/links/${encodeURIComponent(code)}/statistics`;
		if (projectId) return `/api/web/projects/${encodeURIComponent(projectId)}/statistics`;
		return null;
	}
	async function load() {
		const url = endpoint(); if (!url) return message('조회할 프로젝트나 캠페인, 링크가 지정되지 않았습니다.', true);
		const params = new URLSearchParams({from: from.value, to: to.value, bucket: bucket.value}); message('통계를 불러오는 중입니다.');
		try { const response = await fetch(`${url}?${params}`, {headers:{Accept:'application/json'}}); const data = await response.json(); if (!response.ok) throw new Error(response.status===401?'로그인이 필요합니다. 프로젝트 화면에서 로그인해 주세요.':data.message || '통계를 불러올 수 없습니다.'); render(data); }
		catch (error) { message(error.message, true); }
	}
	function render(data) {
		byId('statistics-title').textContent = `${data.name} 통계`; message(`${data.from} ~ ${data.to} · Asia/Seoul`);
		byId('stat-entries').textContent=formatter.format(data.summary.entries); byId('stat-redirects').textContent=formatter.format(data.summary.redirects); byId('stat-nonredirects').textContent=formatter.format(data.summary.nonRedirects); byId('stat-bots').textContent=formatter.format(data.summary.bots);
		byId('stat-entries-delta').textContent=`이전 기간 대비 ${delta(data.summary.entries,data.summary.previousEntries)}`; byId('stat-redirects-delta').textContent=`이전 기간 대비 ${delta(data.summary.redirects,data.summary.previousRedirects)}`; byId('stat-bot-share').textContent=`전체 접근의 ${(data.summary.entries ? data.summary.bots/data.summary.entries*100 : 0).toFixed(1)}%`;
		renderTrend(data.trend); bars(byId('statistics-outcomes'), data.outcomes); bars(byId('statistics-referrers'), data.referrers); renderLinks(data); renderUtm(data); renderCampaigns(data);
	}
	function renderTrend(items) {
		const svg=byId('statistics-trend'), w=960,h=300,p={t:18,r:18,b:38,l:54},pw=w-p.l-p.r,ph=h-p.t-p.b,max=Math.max(1,...items.map(x=>x.entries)); let grid='';
		for(let i=0;i<=4;i++){const y=p.t+ph*i/4;grid+=`<line class="chart-grid" x1="${p.l}" y1="${y}" x2="${w-p.r}" y2="${y}"></line><text class="chart-axis-label" x="${p.l-10}" y="${y+4}" text-anchor="end">${Math.round(max*(1-i/4))}</text>`;}
		const points=key=>items.map((x,i)=>`${p.l+(items.length===1?0:pw*i/(items.length-1))},${p.t+ph*(1-x[key]/max)}`).join(' '); const step=Math.max(1,Math.floor(items.length/6)); const labels=items.map((x,i)=>(i%step===0||i===items.length-1)?`<text class="chart-axis-label" x="${p.l+(items.length===1?0:pw*i/(items.length-1))}" y="${h-12}" text-anchor="middle">${escape(x.date.slice(0,10))}</text>`:'').join('');
		svg.innerHTML=`${grid}<polyline class="chart-line-entries" points="${points('entries')}"></polyline><polyline class="chart-line-redirects" points="${points('redirects')}"></polyline>${labels}`;
	}
	function bars(node,items){node.innerHTML=items.map(x=>`<div class="breakdown-row"><span class="breakdown-label" title="${escape(x.name)}">${escape(x.name)}</span><span class="breakdown-track"><span class="breakdown-fill" style="width:${x.share}%"></span></span><span class="breakdown-value">${formatter.format(x.count)}</span></div>`).join('')||'<p>데이터가 없습니다.</p>';}
	function renderLinks(data){const section=byId('link-status-section');section.hidden=data.scope==='LINK';if(section.hidden)return;byId('statistics-links-wrap').hidden=!data.links.length;byId('link-status-summary').innerHTML=`<span>전체 ${formatter.format(data.summary.totalLinks)}</span><span>확인 ${formatter.format(data.summary.checkedLinks)}</span><span>봇만 ${formatter.format(data.summary.botOnlyLinks)}</span><span>미확인 ${formatter.format(data.summary.uncheckedLinks)}</span>${data.scope==='PROJECT'?`<span>단일 ${formatter.format(data.summary.standaloneLinks)}</span><span>캠페인 소속 ${formatter.format(data.summary.campaignLinks)}</span>`:''}`;byId('statistics-links').innerHTML=data.links.map(x=>`<tr><td><a href="/statistics?projectId=${encodeURIComponent(projectId)}&code=${encodeURIComponent(x.code)}">${escape(x.code)}</a></td><td>${escape(x.externalId||'-')}</td><td>${x.destinationSource==='OWN'?'개별 URL':'캠페인 기본 URL'}</td><td>${status(x.status)}</td><td>${formatter.format(x.entries)}</td><td>${formatter.format(x.redirects)}</td><td>${dateTime(x.lastAccessedAt)}</td></tr>`).join('');}
	function renderUtm(data){const section=byId('utm-section');section.hidden=!data.utm.length;if(section.hidden)return;const max=Math.max(1,...data.utm.map(x=>x.entries));bars(byId('utm-chart'),data.utm.slice(0,10).map(x=>({name:`${x.field}=${x.value}`,count:x.entries,share:x.entries/max*100})));byId('statistics-utm').innerHTML=data.utm.map(x=>`<tr><td>${escape(x.field)}</td><td>${escape(x.value)}</td><td>${x.links}</td><td>${x.accessedLinks}</td><td>${x.botOnlyLinks}</td><td>${x.entries}</td><td>${x.redirects}</td></tr>`).join('');}
	function renderCampaigns(data){const section=byId('campaign-section');section.hidden=!data.campaigns.length;if(section.hidden)return;const max=Math.max(1,...data.campaigns.map(x=>x.entries));bars(byId('campaign-chart'),data.campaigns.map(x=>({name:x.name,count:x.entries,share:x.entries/max*100})));byId('statistics-campaigns').innerHTML=data.campaigns.map(x=>`<tr><td><a href="/statistics?projectId=${encodeURIComponent(projectId)}&campaignId=${x.id}">${escape(x.name)}</a></td><td>${x.links}</td><td>${x.entries}</td><td>${x.redirects}</td></tr>`).join('');}
	function setPeriod(days){const end=new Date(),start=new Date();start.setDate(end.getDate()-days+1);from.value=local(start);to.value=local(end);}
	function delta(now,previous){const value=previous?(now-previous)/previous*100:now?100:0;return `${value>=0?'+':''}${value.toFixed(1)}%`;}
	function status(value){return value==='CHECKED'?'확인됨':value==='BOT_ONLY'?'봇만 접근':'미확인';} function dateTime(value){return value?new Intl.DateTimeFormat('ko-KR',{dateStyle:'short',timeStyle:'short'}).format(new Date(value)):'-';}
	function local(date){return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;} function message(value,error=false){const node=byId('statistics-message');node.textContent=value;node.classList.toggle('error',error);} function escape(value){return String(value).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'",'&#039;');} function byId(id){return document.getElementById(id);}
})();
