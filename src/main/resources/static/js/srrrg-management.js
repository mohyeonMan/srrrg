(function () {
	const app = document.getElementById('management-app');
	if (!app) {
		return;
	}

	const apiUrl = app.dataset.apiUrl || '/api/links';
	const noExpirationValue = '-';
	const numberFormatter = new Intl.NumberFormat('ko-KR');
	const state = {
		secretKey: null,
		link: null,
		sampleMode: false,
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
	const sampleDashboardButton = byId('open-sample-dashboard');
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
		codeInput.value = app.dataset.prefilledCode || '';
		authForm.addEventListener('submit', handleAuthentication);
		codeInput.addEventListener('input', handleAuthInput);
		secretInput.addEventListener('input', handleAuthInput);
		toggleSecretButton.addEventListener('click', toggleSecretVisibility);
		sampleDashboardButton.addEventListener('click', openSampleDashboard);
		changeLinkButton.addEventListener('click', () => requestDiscard(showAuthenticationGate));
		copyManagedShortUrl.addEventListener('click', () => copyToClipboard(managedShortUrl.textContent, copyManagedShortUrl));

		analyticsTab.addEventListener('click', () => requestTab('analytics'));
		settingsTab.addEventListener('click', () => requestTab('settings'));
		analyticsTab.addEventListener('keydown', handleTabKeydown);
		settingsTab.addEventListener('keydown', handleTabKeydown);
		periodButtons.forEach((button) => button.addEventListener('click', () => selectPeriod(Number(button.dataset.period))));

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
			state.sampleMode = false;
			secretInput.value = '';
			openConsole(body);
		} catch (error) {
			showMessage(authMessage, '서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.', true);
		} finally {
			setButtonLoading(authButton, false, '관리 화면 열기');
		}
	}

	function openSampleDashboard() {
		state.secretKey = null;
		state.sampleMode = true;
		openConsole({
			code: 'aB3x9Q',
			shortUrl: 'https://srrrg.link/aB3x9Q',
			originalUrl: 'https://example.com/campaign/summer-release?source=newsletter',
			expiresAt: null,
			statistics: {accessCount: 18420, redirectCount: 16972},
			createdAt: '2026-05-18T03:24:00Z',
			updatedAt: '2026-07-19T11:42:00Z'
		});
	}

	function openConsole(link) {
		state.link = link;
		gate.hidden = true;
		consoleView.hidden = false;
		settingsTab.disabled = state.sampleMode;
		renderIdentity();
		if (!state.sampleMode) {
			renderSettings();
		}
		switchTab('analytics');
		renderAnalytics(state.selectedPeriod);
		managedLinkTitle.focus({preventScroll: true});
	}

	function showAuthenticationGate() {
		state.secretKey = null;
		state.link = null;
		state.sampleMode = false;
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
		managedDestination.textContent = link.originalUrl;
		managedDestination.title = link.originalUrl;
		managedCreatedAt.textContent = formatDateTime(link.createdAt);
		managedUpdatedAt.textContent = formatDateTime(link.updatedAt);
		managedExpirationSummary.textContent = formatExpiration(link.expiresAt);
	}

	function requestTab(tabName) {
		if (tabName === 'settings' && state.sampleMode) {
			return;
		}
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
		renderAnalytics(days);
	}

	function renderAnalytics(days) {
		const analytics = buildSampleAnalytics(days);
		analyticsPeriodLabel.textContent = `최근 ${days}일 · Asia/Seoul`;
		byId('metric-entries').textContent = numberFormatter.format(analytics.summary.entries);
		byId('metric-redirects').textContent = numberFormatter.format(analytics.summary.redirects);
		byId('metric-no-redirect').textContent = numberFormatter.format(analytics.summary.entries - analytics.summary.redirects);
		byId('metric-bots').textContent = numberFormatter.format(analytics.summary.bots);
		byId('metric-entries-delta').textContent = `이전 기간 대비 ${formatDelta(analytics.summary.entriesDelta)}`;
		byId('metric-redirects-delta').textContent = `이전 기간 대비 ${formatDelta(analytics.summary.redirectsDelta)}`;
		byId('metric-bots-share').textContent = `전체 진입의 ${analytics.summary.botShare.toFixed(1)}%`;
		renderTrend(analytics.timeSeries);
		renderBreakdown(byId('referrer-breakdown'), analytics.referrers);
		renderBreakdown(byId('device-breakdown'), analytics.devices);
		renderCompactData(byId('browser-breakdown'), analytics.browsers);
		renderCompactData(byId('os-breakdown'), analytics.operatingSystems);
		renderRecentActivity(analytics.recentActivity);
	}

	function buildSampleAnalytics(days) {
		const today = new Date();
		const timeSeries = [];
		for (let index = days - 1; index >= 0; index--) {
			const date = new Date(today);
			date.setDate(today.getDate() - index);
			const sequence = days - 1 - index;
			const weekday = date.getDay();
			const weekdayFactor = weekday === 0 || weekday === 6 ? -72 : 42;
			const entries = Math.max(120, Math.round(486 + Math.sin(sequence * 0.72) * 88 + Math.cos(sequence * 0.19) * 52 + weekdayFactor));
			const redirects = Math.max(90, entries - Math.round(34 + (sequence % 5) * 7 + Math.abs(Math.sin(sequence)) * 18));
			timeSeries.push({date, entries, redirects});
		}

		const entries = sum(timeSeries, 'entries');
		const redirects = sum(timeSeries, 'redirects');
		const bots = Math.round(entries * 0.072);
		return {
			summary: {
				entries,
				redirects,
				bots,
				botShare: bots / entries * 100,
				entriesDelta: days === 7 ? 8.6 : days === 30 ? 12.4 : 5.8,
				redirectsDelta: days === 7 ? 7.9 : days === 30 ? 10.8 : 4.6
			},
			timeSeries,
			referrers: distribute(entries, [
				['직접 유입', 41.8], ['google.com', 24.7], ['newsletter.example', 16.3], ['github.com', 9.1], ['기타', 8.1]
			]),
			devices: distribute(entries, [
				['모바일', 58.4], ['데스크톱', 34.2], ['태블릿', 4.1], ['봇/기타', 3.3]
			]),
			browsers: distribute(entries, [
				['Chrome', 52.6], ['Safari', 26.9], ['Samsung Internet', 9.8], ['Edge', 6.2], ['기타', 4.5]
			]),
			operatingSystems: distribute(entries, [
				['Android', 38.7], ['iOS', 25.1], ['Windows', 22.8], ['macOS', 10.6], ['기타', 2.8]
			]),
			recentActivity: [
				['오늘 14:32', 'redirected', '실제 이동', 'google.com', '모바일', 'Chrome'],
				['오늘 14:18', 'redirected', '실제 이동', '직접 유입', '데스크톱', 'Safari'],
				['오늘 13:56', 'blocked', '미이동', 'newsletter.example', '모바일', 'Samsung Internet'],
				['오늘 13:41', 'redirected', '실제 이동', 'github.com', '데스크톱', 'Chrome'],
				['오늘 12:27', 'redirected', '실제 이동', '직접 유입', '태블릿', 'Safari']
			]
		};
	}

	function renderTrend(series) {
		const svg = byId('trend-chart');
		const tableBody = byId('trend-data-table');
		const width = 960;
		const height = 300;
		const padding = {top: 18, right: 18, bottom: 38, left: 54};
		const plotWidth = width - padding.left - padding.right;
		const plotHeight = height - padding.top - padding.bottom;
		const maximum = Math.ceil(Math.max(...series.map((item) => item.entries)) / 100) * 100;
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
				<td data-label="시각">${escapeHtml(item[0])}</td>
				<td data-label="결과"><span class="activity-result ${item[1]}">${escapeHtml(item[2])}</span></td>
				<td data-label="유입 경로">${escapeHtml(item[3])}</td>
				<td data-label="디바이스">${escapeHtml(item[4])}</td>
				<td data-label="브라우저">${escapeHtml(item[5])}</td>
			</tr>
		`).join('');
	}

	function renderSettings() {
		if (!state.link || state.sampleMode) {
			return;
		}
		settingsOriginalUrl.value = state.link.originalUrl;
		state.loadedOriginalUrl = state.link.originalUrl;
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
		if (!state.link || state.sampleMode) {
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
		if (!state.link || !state.secretKey || !validateSettings()) {
			return;
		}

		const originalUrl = settingsOriginalUrl.value.trim();
		const expiresValue = getSettingsExpiresValue();
		const requestBody = {};
		if (originalUrl !== state.loadedOriginalUrl) {
			requestBody.originalUrl = originalUrl;
		}
		if (expiresValue !== state.loadedExpiresAt) {
			requestBody.expiresAt = expiresValue ? new Date(expiresValue).toISOString() : null;
		}

		setButtonLoading(saveSettingsButton, true, '저장 중...');
		revertSettingsButton.disabled = true;
		openDeleteDialogButton.disabled = true;
		showMessage(settingsMessage, '변경사항을 저장하고 있습니다.');
		try {
			const response = await fetch(`${apiUrl}/${encodeURIComponent(state.link.code)}`, {
				method: 'PATCH',
				headers: {
					'Content-Type': 'application/json',
					'Accept': 'application/json',
					'X-Srrrg-Secret-Key': state.secretKey
				},
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
		if (!isHttpUrl(originalUrl)) {
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
		if (!state.link || !state.secretKey) {
			return;
		}
		setButtonLoading(confirmDeleteButton, true, '삭제 중...');
		try {
			const response = await fetch(`${apiUrl}/${encodeURIComponent(state.link.code)}`, {
				method: 'DELETE',
				headers: {
					'Accept': 'application/json',
					'X-Srrrg-Secret-Key': state.secretKey
				}
			});
			const body = await readApiBody(response);
			if (!response.ok) {
				deleteDialog.close();
				showMessage(settingsMessage, body.message || '링크를 삭제하지 못했습니다.', true);
				return;
			}
			deleteDialog.close();
			showAuthenticationGate();
			showMessage(authMessage, '링크를 삭제했습니다.');
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

	function distribute(total, entries) {
		return entries.map(([name, share]) => ({name, share, count: Math.round(total * share / 100)}));
	}

	function sum(items, property) {
		return items.reduce((total, item) => total + item[property], 0);
	}

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
