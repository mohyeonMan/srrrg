(function () {
	const app = document.getElementById('statistics-app');
	if (!app) return;
	const base = (app.dataset.base || '/').replace(/\/$/, '');
	const query = new URLSearchParams(location.search);
	let projectId = query.get('projectId');
	let campaignId = query.get('campaignId');
	const formatter = new Intl.NumberFormat('ko-KR');
	const from = byId('statistics-from');
	const to = byId('statistics-to');
	const bucket = byId('statistics-bucket');
	const nextOffsets = { links: null, utm: null, campaigns: null };
	let requestSequence = 0;

	if (query.get('from') && query.get('to')) {
		from.value = query.get('from');
		to.value = query.get('to');
	} else {
		setPeriod(30);
	}
	if (['DAY', 'MONTH', 'YEAR'].includes(query.get('bucket'))) bucket.value = query.get('bucket');

	byId('statistics-filter').addEventListener('submit', (event) => { event.preventDefault(); load(); });
	document.querySelectorAll('[data-days]').forEach((button) => button.addEventListener('click', () => {
		setPeriod(Number(button.dataset.days));
		bucket.value = 'DAY';
		load();
	}));
	// 날짜를 직접 고치면 어떤 칩에도 해당하지 않으므로 선택 표시를 지운다.
	from.addEventListener('input', () => markActivePeriod(null));
	to.addEventListener('input', () => markActivePeriod(null));
	syncActivePeriodFromDates();
	bindMore('links'); bindMore('utm'); bindMore('campaigns');
	// 캠페인 뷰에서는 srrrg-campaigns.js 가 캠페인을 확정한 뒤 reload() 로 호출합니다.
	// 여기서 또 부르면 가장 비싼 통계 쿼리가 두 번 실행됩니다.
	if (!(campaignId && document.getElementById('campaigns-app'))) load();

	function endpoint() {
		if (campaignId) return `${base}/api/web/campaigns/${encodeURIComponent(campaignId)}/statistics`;
		if (projectId) return `${base}/api/web/projects/${encodeURIComponent(projectId)}/statistics`;
		return null;
	}

	async function load(offset = 0, append = null) {
		const url = endpoint();
		if (!url) return message('조회할 프로젝트나 캠페인이 지정되지 않았습니다.', true);
		const sequence = ++requestSequence;
		const params = new URLSearchParams({ from: from.value, to: to.value, bucket: bucket.value, offset, limit: 50 });
		if (!append) updateLocation();
		message(append ? '다음 통계를 불러오는 중입니다.' : '통계를 불러오는 중입니다.');
		setMoreDisabled(true);
		try {
			// SrrrgCommon.request 를 쓴다. 맨 fetch 를 쓰던 동안에는 이 조각만 401 → refresh 재시도에
			// 참여하지 못해서, access token 이 만료된 채 화면에 들어오면 나머지는 전부 살아나는데
			// 통계만 "로그인이 필요합니다" 로 남았다. 그 안내 문구가 사실상 이 결함의 우회였다.
			const response = await SrrrgCommon.request(`${url}?${params}`);
			const data = await SrrrgCommon.body(response);
			if (!response.ok) throw new Error(data.message || '통계를 불러올 수 없습니다.');
			if (sequence !== requestSequence) return;
			if (append) appendPage(data, append); else render(data);
		} catch (error) {
			if (sequence === requestSequence) message(error.message, true);
		} finally {
			if (sequence === requestSequence) setMoreDisabled(false);
		}
	}

	function render(data) {
		const current = data.summary.current;
		const previous = data.summary.previous;
		byId('statistics-title').textContent = `${data.name} 통계`;
		message(`${data.from} ~ ${data.to} · Asia/Seoul`);
		byId('stat-entries').textContent = formatter.format(current.entries);
		byId('stat-redirects').textContent = formatter.format(current.redirects);
		byId('stat-human-entries').textContent = formatter.format(current.humanEntries);
		byId('stat-human-redirects').textContent = formatter.format(current.humanRedirects);
		byId('stat-nonredirects').textContent = formatter.format(current.nonRedirects);
		byId('stat-bots').textContent = formatter.format(current.botEntries);
		byId('stat-entries-delta').textContent = `이전 기간 대비 ${delta(current.entries, previous.entries)}`;
		byId('stat-redirects-delta').textContent = `이전 기간 대비 ${delta(current.redirects, previous.redirects)}`;
		byId('stat-human-rate').textContent = `이동률 ${rate(current.humanRedirects, current.humanEntries)}`;
		byId('stat-bot-share').textContent = `전체 진입의 ${rate(current.botEntries, current.entries)}`;
		renderTrend(data.trend);
		bars(byId('statistics-outcomes'), data.outcomes.map((item) => ({ ...item, name: outcomeLabel(item.outcome) })));
		bars(byId('statistics-referrers'), data.referrers);
		renderLinks(data, false);
		renderUtm(data, false);
		renderCampaigns(data, false);
	}

	function appendPage(data, type) {
		message(`${data.from} ~ ${data.to} · Asia/Seoul`);
		if (type === 'links') renderLinks(data, true);
		if (type === 'utm') renderUtm(data, true);
		if (type === 'campaigns') renderCampaigns(data, true);
	}

	function renderTrend(items) {
		const svg = byId('statistics-trend'), w = 960, h = 300, p = { t: 18, r: 18, b: 38, l: 54 };
		const pw = w - p.l - p.r, ph = h - p.t - p.b;
		const max = Math.max(4, Math.ceil(Math.max(0, ...items.map((item) => item.entries)) / 4) * 4);
		let grid = '';
		for (let index = 0; index <= 4; index++) {
			const y = p.t + ph * index / 4;
			grid += `<line class="chart-grid" x1="${p.l}" y1="${y}" x2="${w - p.r}" y2="${y}"></line><text class="chart-axis-label" x="${p.l - 10}" y="${y + 4}" text-anchor="end">${Math.round(max * (1 - index / 4))}</text>`;
		}
		const points = (key) => items.map((item, index) => `${p.l + (items.length === 1 ? 0 : pw * index / (items.length - 1))},${p.t + ph * (1 - item[key] / max)}`).join(' ');
		const step = Math.max(1, Math.floor(items.length / 6));
		const labels = items.map((item, index) => (index % step === 0 || index === items.length - 1)
			? `<text class="chart-axis-label" x="${p.l + (items.length === 1 ? 0 : pw * index / (items.length - 1))}" y="${h - 12}" text-anchor="middle">${escape(item.periodStart)}</text>` : '').join('');
		svg.innerHTML = `<title>선택 기간 진입 및 이동 추이</title><desc>전체 진입과 실제 이동을 비교합니다.</desc>${grid}<polyline class="chart-line-entries" points="${points('entries')}"></polyline><polyline class="chart-line-redirects" points="${points('redirects')}"></polyline>${labels}`;
	}

	function bars(node, items) {
		node.innerHTML = items.map((item) => `<div class="breakdown-row"><span class="breakdown-label" title="${escape(item.name)}">${escape(item.name)}</span><span class="breakdown-track"><span class="breakdown-fill" style="width:${item.share}%"></span></span><span class="breakdown-value">${formatter.format(item.count)} · ${item.share.toFixed(1)}%</span></div>`).join('') || '<p>선택 기간에 데이터가 없습니다.</p>';
	}

	function renderLinks(data, append) {
		const section = byId('link-status-section');
		section.hidden = data.scope === 'LINK';
		if (section.hidden) return;
		const lifetime = data.summary.lifetimeLinks;
		if (!append) {
			byId('link-status-summary').innerHTML = `<span>전체 ${formatter.format(lifetime.total)}</span><span>사람 유입 ${formatter.format(lifetime.humanAccessed)}</span><span>봇 유입만 ${formatter.format(lifetime.botOnly)}</span><span>유입 없음 ${formatter.format(lifetime.noAccess)}</span>${data.scope === 'PROJECT' ? `<span>단일 ${formatter.format(lifetime.standalone)}</span><span>캠페인 소속 ${formatter.format(lifetime.campaign)}</span>` : ''}`;
		}
		const page = data.links;
		const rows = page.items.map((item) => `<tr><td data-label="링크"><a href="${managementUrl(item.code)}">${escape(item.code)}</a></td><td data-label="우리 쪽 식별자">${escape(item.externalId || '-')}</td><td data-label="목적지">${item.destinationSource === 'LINK' ? '개별 URL' : '캠페인 기본 URL'}</td><td data-label="상태">${status(item.lifetimeStatus)}</td><td data-label="사람 진입">${formatter.format(item.period.humanEntries)}</td><td data-label="사람 이동">${formatter.format(item.period.humanRedirects)}</td><td data-label="최근 유입">${dateTime(item.lastAccessedAt)}</td></tr>`).join('');
		if (append) byId('statistics-links').insertAdjacentHTML('beforeend', rows); else byId('statistics-links').innerHTML = rows;
		byId('statistics-links-wrap').hidden = !byId('statistics-links').children.length;
		setNext('links', page.nextOffset);
	}

	function renderUtm(data, append) {
		const page = data.utm;
		const section = byId('utm-section');
		section.hidden = !page.totalItems;
		if (section.hidden) return;
		if (!append) {
			const max = Math.max(1, ...page.items.map((item) => item.redirectedEvents));
			bars(byId('utm-chart'), page.items.slice(0, 10).map((item) => ({ name: `${item.field}=${item.value}`, count: item.redirectedEvents, share: item.redirectedEvents / max * 100 })));
		}
		const rows = page.items.map((item) => `<tr><td data-label="필드">${escape(item.field)}</td><td data-label="값">${escape(item.value)}</td><td data-label="설정 링크">${formatter.format(item.configuredLinks)}</td><td data-label="이동 링크">${formatter.format(item.lifetimeRedirectedLinks)}</td><td data-label="봇 전용">${formatter.format(item.lifetimeBotOnlyRedirectedLinks)}</td><td data-label="기간 이동">${formatter.format(item.redirectedEvents)}</td></tr>`).join('');
		if (append) byId('statistics-utm').insertAdjacentHTML('beforeend', rows); else byId('statistics-utm').innerHTML = rows;
		setNext('utm', page.nextOffset);
	}

	function renderCampaigns(data, append) {
		const page = data.campaigns;
		const section = byId('campaign-section');
		section.hidden = !page.totalItems;
		if (section.hidden) return;
		if (!append) {
			const max = Math.max(1, ...page.items.map((item) => item.period.humanEntries));
			bars(byId('campaign-chart'), page.items.map((item) => ({ name: item.name, count: item.period.humanEntries, share: item.period.humanEntries / max * 100 })));
		}
		const rows = page.items.map((item) => `<tr><td data-label="캠페인"><a href="${campaignStatisticsUrl(item.id)}">${escape(item.name)}</a></td><td data-label="링크">${formatter.format(item.links)}</td><td data-label="사람 진입">${formatter.format(item.period.humanEntries)}</td><td data-label="사람 이동">${formatter.format(item.period.humanRedirects)}</td><td data-label="이동률">${rate(item.period.humanRedirects, item.period.humanEntries)}</td></tr>`).join('');
		if (append) byId('statistics-campaigns').insertAdjacentHTML('beforeend', rows); else byId('statistics-campaigns').innerHTML = rows;
		setNext('campaigns', page.nextOffset);
	}

	function bindMore(type) {
		byId(`load-more-statistics-${type}`).addEventListener('click', () => {
			if (nextOffsets[type] !== null) load(nextOffsets[type], type);
		});
	}
	function setNext(type, value) { nextOffsets[type] = value; byId(`load-more-statistics-${type}`).hidden = value === null; }
	function setMoreDisabled(disabled) { Object.keys(nextOffsets).forEach((type) => { byId(`load-more-statistics-${type}`).disabled = disabled; }); }
	// 통계에서 여는 링크·캠페인은 워크스페이스(/projects) 안에 머물러야 한다.
	// /statistics 는 라우트가 없고(404), /manage 는 프로젝트 컨텍스트 없는 비회원 화면이다.
	function workspaceUrl(extra) {
		const url = new URL(`${base}/projects`, location.origin);
		url.searchParams.set('projectId', projectId);
		Object.entries(extra).forEach(([key, value]) => url.searchParams.set(key, value));
		url.searchParams.set('from', from.value);
		url.searchParams.set('to', to.value);
		url.searchParams.set('bucket', bucket.value);
		return url.pathname + url.search;
	}
	function managementUrl(code) { return workspaceUrl(campaignId ? { campaignId, linkCode: code } : { linkCode: code }); }
	function campaignStatisticsUrl(id) { return workspaceUrl({ campaignId: id }); }
	function updateLocation() { const url = new URL(location.href); url.searchParams.set('from', from.value); url.searchParams.set('to', to.value); url.searchParams.set('bucket', bucket.value); history.replaceState(null, '', url); }
	function setPeriod(days) { const end = new Date(), start = new Date(); start.setDate(end.getDate() - days + 1); from.value = local(start); to.value = local(end); markActivePeriod(days); }
	function markActivePeriod(days) {
		document.querySelectorAll('[data-days]').forEach((button) => {
			button.setAttribute('aria-pressed', String(Number(button.dataset.days) === days));
		});
	}
	// URL 파라미터로 들어온 기간이 7·30·90일과 정확히 맞으면 그 칩을 선택 상태로 표시한다.
	function syncActivePeriodFromDates() {
		const start = new Date(from.value), end = new Date(to.value);
		if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return markActivePeriod(null);
		const days = Math.round((end - start) / 86_400_000) + 1;
		markActivePeriod([7, 30, 90].includes(days) ? days : null);
	}
	function delta(now, previous) { if (!previous) return now ? '신규 유입' : '변화 없음'; const value = (now - previous) / previous * 100; return `${value >= 0 ? '+' : ''}${value.toFixed(1)}%`; }
	function rate(numerator, denominator) { return denominator ? `${(numerator / denominator * 100).toFixed(1)}%` : '-'; }
	function status(value) { return value === 'HUMAN_ACCESSED' ? '사람 유입 있음' : value === 'BOT_ONLY' ? '봇 유입만' : '유입 없음'; }
	function outcomeLabel(value) { return value === 'REDIRECTED' ? '실제 이동' : value === 'BLOCKED' ? '차단' : value === 'CHECK_FAILED' ? '검사 실패' : value === 'URL_CHANGED' ? 'URL 변경' : '만료'; }
	function dateTime(value) { return value ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '-'; }
	function local(date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`; }
	function message(value, error = false) { const node = byId('statistics-message'); node.textContent = value; node.classList.toggle('error', error); }
	function escape(value) { return String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#039;'); }
	function byId(id) { return document.getElementById(id); }

	window.SrrrgStatistics = {
		reload(newProjectId, newCampaignId) {
			projectId = newProjectId;
			campaignId = newCampaignId;
			load();
		}
	};
})();
