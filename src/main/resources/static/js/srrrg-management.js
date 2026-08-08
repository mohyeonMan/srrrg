(function () {
	const app = document.getElementById('management-app');
	if (!app) {
		return;
	}

	const apiUrl = app.dataset.apiUrl || '/api/links';
	const contextPath = apiUrl.endsWith('/api/links') ? apiUrl.slice(0, -'/api/links'.length) : '';
	const pageQuery = new URLSearchParams(location.search);
	const projectId = pageQuery.get('projectId');
	const requestedCode = extractLinkCode(pageQuery.get('code') || app.dataset.prefilledCode || '');
	const requestedCampaignId = pageQuery.get('campaignId');
	const projectMode = Boolean(projectId && requestedCode);
	const noExpirationValue = '-';
	const numberFormatter = new Intl.NumberFormat('ko-KR');
	const state = {
		secretKey: null,
		link: null,
		selectedPeriod: 30,
		selectedExpiresOption: 'none',
		loadedOriginalUrl: null,
		loadedExpiresAt: null,
		pendingDiscardAction: null,
		copyTimers: new WeakMap()
	};

	const gate = byId('management-gate');
	const consoleView = byId('management-console');
	const authForm = byId('management-auth-form');
	const codeInput = byId('management-code-input');
	const secretInput = byId('management-secret-input');
	const codeError = byId('management-code-error');
	const secretError = byId('management-secret-error');
	const authMessage = byId('management-auth-message');
	const authButton = byId('management-auth-button');
	const toggleSecretButton = byId('toggle-secret-visibility');
	const changeLinkButton = byId('change-managed-link');

	const managedLinkTitle = byId('managed-link-title');
	const managedLinkStatus = byId('managed-link-status');
	const managedShortUrl = byId('managed-short-url');
	const openManagedShortUrl = byId('open-managed-short-url');
	const copyManagedShortUrl = byId('copy-managed-short-url');
	const managedDestination = byId('managed-destination');
	const managedCreatedAt = byId('managed-created-at');
	const managedUpdatedAt = byId('managed-updated-at');
	const managedExpirationSummary = byId('managed-expiration-summary');

	const analyticsTab = byId('analytics-tab');
	const settingsTab = byId('settings-tab');
	const analyticsPanel = byId('analytics-panel');
	const settingsPanel = byId('settings-panel');
	const periodButtons = document.querySelectorAll('[data-period]');
	const analyticsPeriodLabel = byId('analytics-period-label');
	const analyticsFrom = byId('analytics-from');
	const analyticsTo = byId('analytics-to');
	const analyticsBucket = byId('analytics-bucket');

	const settingsForm = byId('management-settings-form');
	const settingsOriginalUrl = byId('settings-original-url');
	const settingsOriginalUrlError = byId('settings-original-url-error');
	const settingsExpiresAt = byId('settings-expires-at');
	const settingsExpirationError = byId('settings-expiration-error');
	const settingsMessage = byId('settings-message');
	const settingsExpireOptions = document.querySelectorAll('[data-settings-expires-option]');
	const revertSettingsButton = byId('revert-settings');
	const saveSettingsButton = byId('save-settings');
	const openDeleteDialogButton = byId('open-delete-dialog');
	const deleteDialog = byId('delete-link-dialog');
	const confirmDeleteButton = byId('confirm-delete-link');
	const discardDialog = byId('discard-changes-dialog');
	const confirmDiscardButton = byId('confirm-discard-changes');

	init();

	function init() {
		codeInput.value = requestedCode || '';
		authForm.addEventListener('submit', handleAuthentication);
		codeInput.addEventListener('input', handleAuthInput);
		secretInput.addEventListener('input', handleAuthInput);
		toggleSecretButton.addEventListener('click', toggleSecretVisibility);
		changeLinkButton.addEventListener('click', () => requestDiscard(projectMode ? returnToList : showAuthenticationGate));
		copyManagedShortUrl.addEventListener('click', () => copyToClipboard(managedShortUrl.textContent, copyManagedShortUrl));

		analyticsTab.addEventListener('click', () => requestTab('analytics'));
		settingsTab.addEventListener('click', () => requestTab('settings'));
		analyticsTab.addEventListener('keydown', handleTabKeydown);
		settingsTab.addEventListener('keydown', handleTabKeydown);
		periodButtons.forEach((button) => button.addEventListener('click', () => selectPeriod(Number(button.dataset.period))));
		byId('analytics-custom-period').addEventListener('submit', (event) => { event.preventDefault(); periodButtons.forEach(button => button.setAttribute('aria-pressed', 'false')); renderAnalytics(); });
		setAnalyticsDates(state.selectedPeriod);

		settingsForm.addEventListener('submit', handleSettingsSave);
		settingsOriginalUrl.addEventListener('input', handleSettingsInput);
		settingsExpiresAt.addEventListener('input', handleSettingsDateInput);
		settingsExpiresAt.addEventListener('change', handleSettingsDateInput);
		settingsExpireOptions.forEach((button) => {
			button.addEventListener('click', () => selectSettingsExpiration(button.dataset.settingsExpiresOption));
		});
		revertSettingsButton.addEventListener('click', renderSettings);
		openDeleteDialogButton.addEventListener('click', () => deleteDialog.showModal());
		confirmDeleteButton.addEventListener('click', handleDelete);
		confirmDiscardButton.addEventListener('click', handleDiscardConfirmation);
		window.addEventListener('beforeunload', protectUnsavedChanges);
		if (projectMode) {
			authForm.hidden = true;
			gate.querySelector('.credential-privacy-note').hidden = true;
			byId('management-gate-title').textContent = '링크 상세';
			gate.querySelector('.management-gate-heading > p:last-child').textContent = '링크 정보와 권한을 확인하고 있습니다.';
			changeLinkButton.textContent = requestedCampaignId ? '캠페인으로' : '프로젝트로';
			loadProjectLink();
		}
	}

	async function loadProjectLink() {
		try {
			const response = await fetch(projectLinkUrl(), {headers: {'Accept': 'application/json'}});
			const body = await readApiBody(response);
			if (!response.ok) throw new Error(body.message || '링크를 확인하지 못했습니다.');
			openConsole(body);
		} catch (error) {
			showMessage(authMessage, error.message, true);
		}
	}

	function handleAuthInput() {
		clearFieldError(codeInput, codeError);
		clearFieldError(secretInput, secretError);
		showMessage(authMessage, '');
	}

	function toggleSecretVisibility() {
		const reveal = secretInput.type === 'password';
		secretInput.type = reveal ? 'text' : 'password';
		toggleSecretButton.textContent = reveal ? '숨기기' : '보기';
		toggleSecretButton.setAttribute('aria-pressed', String(reveal));
	}

	async function handleAuthentication(event) {
		event.preventDefault();
		const code = extractLinkCode(codeInput.value);
		const secretKey = secretInput.value.trim();
		let valid = true;

		if (!code) {
			setFieldError(codeInput, codeError, '6자리 단축 코드 또는 올바른 단축 URL을 입력하세요.');
			valid = false;
		}
		if (!/^srrrg_sk_[0-9A-Za-z_-]{43}$/.test(secretKey)) {
			setFieldError(secretInput, secretError, '발급받은 전체 secret key를 입력하세요.');
			valid = false;
		}
		if (!valid) {
			return;
		}

		setButtonLoading(authButton, true, '확인 중...');
		showMessage(authMessage, '링크 관리 권한을 확인하고 있습니다.');
		try {
			const response = await fetch(`${apiUrl}/${encodeURIComponent(code)}`, {
				headers: {
					'Accept': 'application/json',
					'X-Srrrg-Secret-Key': secretKey
				}
			});
			const body = await readApiBody(response);
			if (!response.ok) {
				showMessage(authMessage, body.message || '링크를 확인하지 못했습니다.', true);
				return;
			}

			state.secretKey = secretKey;
			secretInput.value = '';
			openConsole(body);
		} catch (error) {
			showMessage(authMessage, '서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.', true);
		} finally {
			setButtonLoading(authButton, false, '관리 화면 열기');
		}
	}

	function openConsole(link) {
		state.link = link;
		gate.hidden = true;
		consoleView.hidden = false;
		settingsTab.disabled = !link.editable;
		renderIdentity();
		renderSettings();
		switchTab('analytics');
		renderAnalytics(state.selectedPeriod);
		managedLinkTitle.focus({preventScroll: true});
	}

	function showAuthenticationGate() {
		state.secretKey = null;
		state.link = null;
		state.pendingDiscardAction = null;
		consoleView.hidden = true;
		gate.hidden = false;
		settingsTab.disabled = false;
		showMessage(authMessage, '');
		codeInput.focus({preventScroll: true});
	}

	function renderIdentity() {
		const link = state.link;
		const expired = link.expiresAt && new Date(link.expiresAt).getTime() <= Date.now();
		managedLinkStatus.textContent = expired ? '만료됨' : '사용 가능';
		managedLinkStatus.className = expired ? 'status-badge warning' : 'status-badge success';
		managedShortUrl.textContent = link.shortUrl;
		managedShortUrl.href = link.shortUrl;
		openManagedShortUrl.href = link.shortUrl;
		managedDestination.textContent = link.originalUrl || '캠페인 기본 목적지 사용';
		managedDestination.title = link.originalUrl || '';
		managedCreatedAt.textContent = formatDateTime(link.createdAt);
		managedUpdatedAt.textContent = formatDateTime(link.updatedAt);
		managedExpirationSummary.textContent = formatExpiration(link.expiresAt);
	}

	function requestTab(tabName) {
		if (tabName === 'analytics' && hasSettingsChanges()) {
			requestDiscard(() => switchTab('analytics'));
			return;
		}
		switchTab(tabName);
	}

	function handleTabKeydown(event) {
		if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
			return;
		}
		event.preventDefault();
		if (event.currentTarget === analyticsTab && !settingsTab.disabled) {
			settingsTab.focus();
			requestTab('settings');
			return;
		}
		analyticsTab.focus();
		requestTab('analytics');
	}

	function switchTab(tabName) {
		const showAnalytics = tabName === 'analytics';
		analyticsTab.setAttribute('aria-selected', String(showAnalytics));
		settingsTab.setAttribute('aria-selected', String(!showAnalytics));
		analyticsTab.tabIndex = showAnalytics ? 0 : -1;
		settingsTab.tabIndex = showAnalytics ? -1 : 0;
		analyticsPanel.hidden = !showAnalytics;
		settingsPanel.hidden = showAnalytics;
	}

	function selectPeriod(days) {
		state.selectedPeriod = days;
		periodButtons.forEach((button) => button.setAttribute('aria-pressed', String(Number(button.dataset.period) === days)));
		setAnalyticsDates(days);
		analyticsBucket.value = 'DAY';
		renderAnalytics(days);
	}

	async function renderAnalytics(days) {
		analyticsPeriodLabel.textContent = `${analyticsFrom.value} ~ ${analyticsTo.value} · Asia/Seoul`;
		const query = new URLSearchParams({from: analyticsFrom.value, to: analyticsTo.value, bucket: analyticsBucket.value});
		let analytics;
		try {
			const response = await fetch(statisticsUrl(query), {
				headers: requestHeaders()
			});
			analytics = await readApiBody(response);
			if (!response.ok) throw new Error(analytics.message || '통계를 불러올 수 없습니다.');
		} catch (error) {
			analyticsPeriodLabel.textContent = error.message;
			return;
		}
		byId('metric-entries').textContent = numberFormatter.format(analytics.summary.entries);
		byId('metric-redirects').textContent = numberFormatter.format(analytics.summary.redirects);
		byId('metric-no-redirect').textContent = numberFormatter.format(analytics.summary.nonRedirects);
		byId('metric-bots').textContent = numberFormatter.format(analytics.summary.bots);
		byId('metric-entries-delta').textContent = `이전 기간 대비 ${formatDelta(delta(analytics.summary.entries, analytics.summary.previousEntries))}`;
		byId('metric-redirects-delta').textContent = `이전 기간 대비 ${formatDelta(delta(analytics.summary.redirects, analytics.summary.previousRedirects))}`;
		byId('metric-bots-share').textContent = `전체 진입의 ${(analytics.summary.entries ? analytics.summary.bots / analytics.summary.entries * 100 : 0).toFixed(1)}%`;
		renderTrend(analytics.trend.map((item) => ({date: new Date(item.date.replace(' ', 'T')), entries: item.entries, redirects: item.redirects})));
		renderBreakdown(byId('referrer-breakdown'), analytics.referrers);
		renderBreakdown(byId('device-breakdown'), analytics.devices);
		renderCompactData(byId('browser-breakdown'), analytics.browsers);
		renderCompactData(byId('os-breakdown'), analytics.operatingSystems);
		renderRecentActivity(analytics.recentActivity);
	}

	function renderTrend(series) {
		const svg = byId('trend-chart');
		const tableBody = byId('trend-data-table');
		const width = 960;
		const height = 300;
		const padding = {top: 18, right: 18, bottom: 38, left: 54};
		const plotWidth = width - padding.left - padding.right;
		const plotHeight = height - padding.top - padding.bottom;
		const maximum = Math.max(1, Math.ceil(Math.max(0, ...series.map((item) => item.entries)) / 100) * 100);
		const gridLines = [];
		for (let index = 0; index <= 4; index++) {
			const y = padding.top + plotHeight * index / 4;
			const value = Math.round(maximum * (1 - index / 4));
			gridLines.push(`<line class="chart-grid" x1="${padding.left}" y1="${y}" x2="${width - padding.right}" y2="${y}"></line>`);
			gridLines.push(`<text class="chart-axis-label" x="${padding.left - 10}" y="${y + 4}" text-anchor="end">${value}</text>`);
		}

		const entriesPoints = createChartPoints(series, 'entries', maximum, padding, plotWidth, plotHeight);
		const redirectsPoints = createChartPoints(series, 'redirects', maximum, padding, plotWidth, plotHeight);
		const labelStep = Math.max(1, Math.floor(series.length / 6));
		const labels = series.map((item, index) => {
			if (index % labelStep !== 0 && index !== series.length - 1) {
				return '';
			}
			const x = padding.left + (series.length === 1 ? 0 : plotWidth * index / (series.length - 1));
			return `<text class="chart-axis-label" x="${x}" y="${height - 12}" text-anchor="middle">${formatMonthDay(item.date)}</text>`;
		}).join('');

		svg.innerHTML = `<title id="trend-chart-title">일별 진입 및 실제 이동 추이</title><desc id="trend-chart-description">선택 기간 동안의 일별 진입 수와 실제 이동 수를 비교합니다.</desc>${gridLines.join('')}<polyline class="chart-line-entries" points="${entriesPoints}"></polyline><polyline class="chart-line-redirects" points="${redirectsPoints}"></polyline>${labels}`;
		tableBody.innerHTML = series.map((item) => `<tr><td>${formatDate(item.date)}</td><td>${item.entries}</td><td>${item.redirects}</td></tr>`).join('');
	}

	function createChartPoints(series, property, maximum, padding, plotWidth, plotHeight) {
		return series.map((item, index) => {
			const x = padding.left + (series.length === 1 ? 0 : plotWidth * index / (series.length - 1));
			const y = padding.top + plotHeight * (1 - item[property] / maximum);
			return `${x.toFixed(2)},${y.toFixed(2)}`;
		}).join(' ');
	}

	function renderBreakdown(container, items) {
		container.innerHTML = items.map((item) => `
			<div class="breakdown-row">
				<span class="breakdown-label" title="${escapeHtml(item.name)}">${escapeHtml(item.name)}</span>
				<span class="breakdown-track" aria-hidden="true"><span class="breakdown-fill" style="width:${item.share}%"></span></span>
				<span class="breakdown-value">${item.share.toFixed(1)}%</span>
			</div>
		`).join('');
	}

	function renderCompactData(container, items) {
		container.innerHTML = items.map((item) => `
			<div class="compact-data-row">
				<span class="compact-data-name">${escapeHtml(item.name)}</span>
				<span class="compact-data-count">${numberFormatter.format(item.count)}</span>
				<span class="compact-data-share">${item.share.toFixed(1)}%</span>
			</div>
		`).join('');
	}

	function renderRecentActivity(items) {
		byId('recent-activity-body').innerHTML = items.map((item) => `
			<tr>
				<td data-label="시각">${escapeHtml(formatDateTime(item.accessedAt))}</td>
				<td data-label="결과"><span class="activity-result ${item.outcome.toLowerCase()}">${escapeHtml(outcomeLabel(item.outcome))}</span></td>
				<td data-label="유입 경로">${escapeHtml(item.referrerDomain)}</td>
				<td data-label="디바이스">${escapeHtml(item.device)}</td>
				<td data-label="브라우저">${escapeHtml(item.browser)}</td>
			</tr>
		`).join('');
	}

	function renderSettings() {
		if (!state.link) {
			return;
		}
		settingsOriginalUrl.value = state.link.originalUrl || '';
		settingsOriginalUrl.placeholder = state.link.campaignId ? '비우면 캠페인 기본 목적지를 사용합니다' : 'https://example.com';
		state.loadedOriginalUrl = state.link.originalUrl || '';
		if (state.link.expiresAt) {
			state.selectedExpiresOption = 'custom';
			setDateTimeInputMode(settingsExpiresAt, true, toDatetimeLocal(new Date(state.link.expiresAt)));
			state.loadedExpiresAt = settingsExpiresAt.value;
		} else {
			state.selectedExpiresOption = 'none';
			setDateTimeInputMode(settingsExpiresAt, false, noExpirationValue);
			state.loadedExpiresAt = '';
		}
		updateSettingsExpireButtons();
		clearFieldError(settingsOriginalUrl, settingsOriginalUrlError);
		clearFieldError(settingsExpiresAt, settingsExpirationError);
		showMessage(settingsMessage, '');
		updateSettingsActions();
	}

	function handleSettingsInput() {
		clearFieldError(settingsOriginalUrl, settingsOriginalUrlError);
		showMessage(settingsMessage, '');
		updateSettingsActions();
	}

	function handleSettingsDateInput() {
		state.selectedExpiresOption = 'custom';
		updateSettingsExpireButtons();
		clearFieldError(settingsExpiresAt, settingsExpirationError);
		showMessage(settingsMessage, '');
		updateSettingsActions();
	}

	function selectSettingsExpiration(option) {
		state.selectedExpiresOption = option;
		updateSettingsExpireButtons();
		showMessage(settingsMessage, '');
		clearFieldError(settingsExpiresAt, settingsExpirationError);
		if (option === 'none') {
			setDateTimeInputMode(settingsExpiresAt, false, noExpirationValue);
			updateSettingsActions();
			return;
		}
		if (option === 'custom') {
			const value = settingsExpiresAt.value === noExpirationValue || !settingsExpiresAt.value
					? toDatetimeLocal(addTime(new Date(), 1, 'hours'))
					: settingsExpiresAt.value;
			setDateTimeInputMode(settingsExpiresAt, true, value);
			settingsExpiresAt.focus();
			updateSettingsActions();
			return;
		}

		const [amount, unit] = option.split(':');
		setDateTimeInputMode(settingsExpiresAt, false, toDatetimeLocal(addTime(new Date(), Number(amount), unit)));
		updateSettingsActions();
	}

	function updateSettingsExpireButtons() {
		settingsExpireOptions.forEach((button) => {
			button.setAttribute('aria-pressed', String(button.dataset.settingsExpiresOption === state.selectedExpiresOption));
		});
	}

	function hasSettingsChanges() {
		if (!state.link) {
			return false;
		}
		return settingsOriginalUrl.value.trim() !== state.loadedOriginalUrl
				|| getSettingsExpiresValue() !== state.loadedExpiresAt;
	}

	function updateSettingsActions() {
		const loading = saveSettingsButton.dataset.loading === 'true';
		const changed = hasSettingsChanges();
		saveSettingsButton.disabled = loading || !changed;
		revertSettingsButton.disabled = loading || !changed;
	}

	async function handleSettingsSave(event) {
		event.preventDefault();
		if (!state.link || !isAuthorized() || !validateSettings()) {
			return;
		}

		const originalUrl = settingsOriginalUrl.value.trim();
		const expiresValue = getSettingsExpiresValue();
		const requestBody = {};
		if (originalUrl !== state.loadedOriginalUrl) {
			requestBody.originalUrl = originalUrl || null;
		}
		if (expiresValue !== state.loadedExpiresAt) {
			requestBody.expiresAt = expiresValue ? new Date(expiresValue).toISOString() : null;
		}

		setButtonLoading(saveSettingsButton, true, '저장 중...');
		revertSettingsButton.disabled = true;
		openDeleteDialogButton.disabled = true;
		showMessage(settingsMessage, '변경사항을 저장하고 있습니다.');
		try {
			const response = await fetch(linkUrl(), {
				method: 'PATCH',
				headers: requestHeaders(true),
				body: JSON.stringify(requestBody)
			});
			const body = await readApiBody(response);
			if (!response.ok) {
				showMessage(settingsMessage, body.message || '링크를 수정하지 못했습니다.', true);
				return;
			}
			state.link = body;
			renderIdentity();
			renderSettings();
			showMessage(settingsMessage, '변경사항을 저장했습니다.');
		} catch (error) {
			showMessage(settingsMessage, '서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.', true);
		} finally {
			setButtonLoading(saveSettingsButton, false, '변경 저장');
			openDeleteDialogButton.disabled = false;
			updateSettingsActions();
		}
	}

	function validateSettings() {
		let valid = true;
		const originalUrl = settingsOriginalUrl.value.trim();
		if ((!originalUrl && !state.link.campaignId) || (originalUrl && !isHttpUrl(originalUrl))) {
			setFieldError(settingsOriginalUrl, settingsOriginalUrlError, 'http 또는 https로 시작하는 올바른 URL을 입력하세요.');
			valid = false;
		}
		const expiresValue = getSettingsExpiresValue();
		if (expiresValue && new Date(expiresValue).getTime() <= Date.now()) {
			setFieldError(settingsExpiresAt, settingsExpirationError, '만료 시각은 현재보다 이후여야 합니다.');
			valid = false;
		}
		return valid;
	}

	async function handleDelete(event) {
		event.preventDefault();
		if (!state.link || !isAuthorized()) {
			return;
		}
		setButtonLoading(confirmDeleteButton, true, '삭제 중...');
		try {
			const response = await fetch(linkUrl(), {
				method: 'DELETE',
				headers: requestHeaders()
			});
			const body = await readApiBody(response);
			if (!response.ok) {
				deleteDialog.close();
				showMessage(settingsMessage, body.message || '링크를 삭제하지 못했습니다.', true);
				return;
			}
			deleteDialog.close();
			if (projectMode) returnToList();
			else {
				showAuthenticationGate();
				showMessage(authMessage, '링크를 삭제했습니다.');
			}
		} catch (error) {
			deleteDialog.close();
			showMessage(settingsMessage, '서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.', true);
		} finally {
			setButtonLoading(confirmDeleteButton, false, '삭제하기');
		}
	}

	function requestDiscard(action) {
		if (!hasSettingsChanges()) {
			action();
			return;
		}
		state.pendingDiscardAction = action;
		discardDialog.showModal();
	}

	function handleDiscardConfirmation(event) {
		event.preventDefault();
		const action = state.pendingDiscardAction;
		state.pendingDiscardAction = null;
		renderSettings();
		discardDialog.close();
		if (action) {
			action();
		}
	}

	function protectUnsavedChanges(event) {
		if (!hasSettingsChanges()) {
			return;
		}
		event.preventDefault();
		event.returnValue = '';
	}

	function getSettingsExpiresValue() {
		return state.selectedExpiresOption === 'none' ? '' : settingsExpiresAt.value;
	}

	function projectLinkUrl() { return `${contextPath}/api/web/projects/${encodeURIComponent(projectId)}/links/${encodeURIComponent(requestedCode)}`; }
	function linkUrl() { return projectMode ? projectLinkUrl() : `${apiUrl}/${encodeURIComponent(state.link.code)}`; }
	function statisticsUrl(query) { return projectMode ? `${projectLinkUrl()}/statistics?${query}` : `${linkUrl()}/statistics?${query}`; }
	function requestHeaders(json = false) {
		const headers = {'Accept': 'application/json'};
		if (json) headers['Content-Type'] = 'application/json';
		if (projectMode) headers['X-XSRF-TOKEN'] = csrf();
		else headers['X-Srrrg-Secret-Key'] = state.secretKey;
		return headers;
	}
	function isAuthorized() { return projectMode || Boolean(state.secretKey); }
	function csrf() { return decodeURIComponent(document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)?.[1] || ''); }
	function returnToList() { location.href = requestedCampaignId ? `${contextPath}/campaigns?projectId=${encodeURIComponent(projectId)}&campaignId=${encodeURIComponent(requestedCampaignId)}` : `${contextPath}/projects?projectId=${encodeURIComponent(projectId)}`; }

	function delta(current, previous) { return previous ? (current - previous) / previous * 100 : current ? 100 : 0; }
	function outcomeLabel(value) { return value === 'REDIRECTED' ? '실제 이동' : value === 'BLOCKED' ? '차단' : value === 'CHECK_FAILED' ? '검사 실패' : 'URL 변경'; }
	function localDate(date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`; }
	function setAnalyticsDates(days) { const end = new Date(), start = new Date(); start.setDate(end.getDate() - days + 1); analyticsFrom.value = localDate(start); analyticsTo.value = localDate(end); }

	function formatDelta(value) {
		return `${value >= 0 ? '+' : ''}${value.toFixed(1)}%`;
	}

	function formatExpiration(value) {
		if (!value) {
			return '만료 없음';
		}
		const date = new Date(value);
		return `${date.getTime() <= Date.now() ? '만료됨 · ' : ''}${formatDateTime(value)}`;
	}

	function formatDateTime(value) {
		if (!value) {
			return '-';
		}
		return new Intl.DateTimeFormat('ko-KR', {dateStyle: 'medium', timeStyle: 'short'}).format(new Date(value));
	}

	function formatDate(date) {
		return new Intl.DateTimeFormat('ko-KR', {year: 'numeric', month: '2-digit', day: '2-digit'}).format(date);
	}

	function formatMonthDay(date) {
		return new Intl.DateTimeFormat('ko-KR', {month: 'numeric', day: 'numeric'}).format(date);
	}

	function extractLinkCode(value) {
		const trimmed = value.trim();
		if (/^[0-9A-Za-z]{6}$/.test(trimmed)) {
			return trimmed;
		}
		try {
			const url = new URL(trimmed.includes('://') ? trimmed : `https://${trimmed}`);
			const code = url.pathname.split('/').filter(Boolean).at(-1) || '';
			return /^[0-9A-Za-z]{6}$/.test(code) ? code : null;
		} catch (error) {
			return null;
		}
	}

	function isHttpUrl(value) {
		try {
			const url = new URL(value);
			return url.protocol === 'http:' || url.protocol === 'https:';
		} catch (error) {
			return false;
		}
	}

	function addTime(date, amount, unit) {
		const result = new Date(date);
		if (unit === 'hours') result.setHours(result.getHours() + amount);
		if (unit === 'days') result.setDate(result.getDate() + amount);
		if (unit === 'months') result.setMonth(result.getMonth() + amount);
		if (unit === 'years') result.setFullYear(result.getFullYear() + amount);
		return result;
	}

	function toDatetimeLocal(date) {
		const year = date.getFullYear();
		const month = String(date.getMonth() + 1).padStart(2, '0');
		const day = String(date.getDate()).padStart(2, '0');
		const hour = String(date.getHours()).padStart(2, '0');
		const minute = String(date.getMinutes()).padStart(2, '0');
		return `${year}-${month}-${day}T${hour}:${minute}`;
	}

	function setDateTimeInputMode(input, editable, value) {
		input.type = value === noExpirationValue ? 'text' : 'datetime-local';
		input.disabled = !editable;
		input.value = value;
	}

	async function copyToClipboard(value, button) {
		if (!value) return;
		try {
			if (navigator.clipboard && window.isSecureContext) {
				await navigator.clipboard.writeText(value);
			} else {
				const textarea = document.createElement('textarea');
				textarea.value = value;
				textarea.setAttribute('readonly', '');
				textarea.style.position = 'fixed';
				textarea.style.top = '-999px';
				document.body.appendChild(textarea);
				textarea.select();
				const copied = document.execCommand('copy');
				document.body.removeChild(textarea);
				if (!copied) throw new Error('Clipboard copy failed');
			}
			flashCopyButton(button, '복사됨');
		} catch (error) {
			flashCopyButton(button, '실패');
		}
	}

	function flashCopyButton(button, text) {
		const originalText = button.dataset.originalText || button.textContent;
		button.dataset.originalText = originalText;
		button.textContent = text;
		clearTimeout(state.copyTimers.get(button));
		state.copyTimers.set(button, window.setTimeout(() => {
			button.textContent = button.dataset.originalText;
		}, 1500));
	}

	function setFieldError(input, element, message) {
		input.setAttribute('aria-invalid', 'true');
		element.textContent = message;
		element.hidden = false;
	}

	function clearFieldError(input, element) {
		input.removeAttribute('aria-invalid');
		element.textContent = '';
		element.hidden = true;
	}

	function showMessage(element, message, error = false) {
		element.textContent = message;
		element.classList.toggle('error', error);
	}

	function setButtonLoading(button, loading, text) {
		button.dataset.loading = String(loading);
		button.disabled = loading;
		button.textContent = text;
	}

	async function readApiBody(response) {
		try {
			return await response.json();
		} catch (error) {
			return {};
		}
	}

	function escapeHtml(value) {
		return String(value)
				.replaceAll('&', '&amp;')
				.replaceAll('<', '&lt;')
				.replaceAll('>', '&gt;')
				.replaceAll('"', '&quot;')
				.replaceAll("'", '&#039;');
	}

	function byId(id) {
		return document.getElementById(id);
	}
})();
