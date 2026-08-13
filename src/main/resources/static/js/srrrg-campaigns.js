(() => {
	const app = document.querySelector('#campaigns-app');
	if (!app) return;
	const embeddedView = app.closest('#project-campaign-view');
	if (embeddedView?.hidden) return;

	const base = SrrrgCommon.base;
	const { request, requestShared, body, element, replaceChildren, setMessage, submitting, confirmAction } = SrrrgCommon;
	const params = new URLSearchParams(location.search);
	const state = {
		projectId: params.get('projectId'),
		campaignId: params.get('campaignId') ? Number(params.get('campaignId')) : null,
		campaign: null,
		activeFields: [],
		utmDefaults: {},
		selectedLinkCodes: new Set(),
		linksCursor: null,
		importPollHandle: null,
		subdomain: null,
		subdomainEnabled: false
	};
	const byId = (id) => document.getElementById(id);
	const campaignsMessage = byId('campaigns-message');
	const TABS = ['overview', 'link-list', 'link-create', 'settings'];

	function formatDate(value) {
		return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
	}

	// 표 안에서는 축약형을 쓴다. 전체 시각(약 184px)이 열 하나를 다 먹어서
	// 뒤쪽 열이 좁은 데스크톱에서 잘려 나갔다. 전체 시각은 title 로 남긴다.
	function compactDate(value) {
		return new Intl.DateTimeFormat('ko-KR', { month: '2-digit', day: '2-digit' }).format(new Date(value));
	}

	// 탭 묶음(roving tabindex, 좌우 순환 방향키)은 SrrrgCommon.tabs 가 처리한다.
	// 이 조각은 campaignId 가 있고 뷰가 보일 때만 실행되므로(위 가드) ?tab= 을 그대로 읽는다.
	const campaignTabs = SrrrgCommon.tabs({
		list: TABS, prefix: 'campaign', param: 'tab', initial: params.get('tab')
	});

	// 링크가 0개인 빈 상태에서 바로 만들기로 넘어간다. 사용자가 누른 이동이므로
	// 탭을 직접 클릭한 것과 같이 주소에도 반영한다.
	byId('go-create-campaign-link').addEventListener('click', () => {
		campaignTabs.switchTo('link-create', true);
		byId('campaign-tab-link-create').focus();
	});

	if (!state.projectId) {
		setMessage(campaignsMessage, '프로젝트를 먼저 선택하세요.', true);
		return;
	}
	byId('manage-utm-templates-link').href = `${base}/projects?projectId=${state.projectId}&view=utm-templates`;

	async function loadCampaignPicker() {
		const response = await request(`${base}/api/web/projects/${state.projectId}/campaigns?limit=100`);
		if (!response.ok) {
			setMessage(campaignsMessage, (await body(response)).message || '캠페인을 불러올 수 없습니다.', true);
			return;
		}
		const page = await body(response);
		const items = page.items || [];
		const options = items.map((campaign) => {
			const option = element('option', '', campaign.name);
			option.value = String(campaign.id);
			return option;
		});
		replaceChildren(byId('campaign-picker'), options.length ? options : [element('option', '', '캠페인이 없습니다')]);
		if (!items.length) {
			byId('campaign-body').hidden = true;
			return;
		}
		const initial = items.find((campaign) => campaign.id === state.campaignId) || items[0];
		byId('campaign-picker').value = String(initial.id);
		await selectCampaign(initial.id);
	}

	byId('campaign-picker').addEventListener('change', (event) => selectCampaign(Number(event.target.value)));

	async function selectCampaign(campaignId) {
		state.campaignId = campaignId;
		const url = new URL(location.href);
		url.searchParams.set('campaignId', String(campaignId));
		history.replaceState(null, '', url);

		const response = await request(`${base}/api/web/campaigns/${campaignId}`);
		if (!response.ok) {
			setMessage(campaignsMessage, (await body(response)).message || '캠페인을 불러올 수 없습니다.', true);
			return;
		}
		state.campaign = await response.json();
		byId('campaign-body').hidden = false;
		byId('campaign-name').textContent = state.campaign.name;
		byId('campaign-description').textContent = state.campaign.description || '';
		byId('campaign-info-name').value = state.campaign.name;
		byId('campaign-info-description').value = state.campaign.description || '';
		byId('campaign-default-url').value = state.campaign.defaultOriginalUrl || '';
		updateOriginalUrlPlaceholder();
		byId('download-template-link').href = `${base}/api/web/campaigns/${campaignId}/links/template.csv`;
		window.SrrrgStatistics?.reload(state.projectId, state.campaignId);

		await loadTemplates();
		await refreshTemplateSelection();
		await loadLinks(null);
		resetImportPanel();
	}

	byId('campaign-info-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const data = new FormData(event.target);
		const name = data.get('name')?.trim();
		const description = data.get('description')?.trim() || null;
		if (!name) return setMessage(byId('campaign-info-message'), '이름을 입력하세요.', true);
		await submitting(event.submitter, '저장 중...', async () => {
			setMessage(byId('campaign-info-message'), '캠페인 정보를 저장하고 있습니다...');
			const response = await request(`${base}/api/web/campaigns/${state.campaignId}`, {
				method: 'PATCH', body: JSON.stringify({ name, description })
			});
			const responseBody = await body(response);
			if (!response.ok) return setMessage(byId('campaign-info-message'), responseBody.message || '캠페인 정보를 저장할 수 없습니다.', true);
			state.campaign = responseBody;
			byId('campaign-name').textContent = state.campaign.name;
			byId('campaign-description').textContent = state.campaign.description || '';
			setMessage(byId('campaign-info-message'), '캠페인 정보를 저장했습니다.');
		});
	});

	byId('default-destination-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const submitter = event.submitter;
		const nextUrl = new FormData(event.target).get('defaultOriginalUrl')?.trim() || null;
		if (state.campaign.defaultOriginalUrl && !nextUrl && !(await confirmAction({
			title: '기본 목적지를 제거할까요?',
			body: '자체 URL이 없는 링크는 목적지가 사라져 이동하지 않습니다. 링크 주소는 그대로 남아 있고, 기본 목적지를 다시 설정하면 즉시 되살아납니다.',
			confirmLabel: '제거'
		}))) return;
		await submitting(submitter, '저장 중...', async () => {
			setMessage(byId('default-destination-message'), '기본 목적지를 저장하고 있습니다...');
			const response = await request(`${base}/api/web/campaigns/${state.campaignId}`, {
				method: 'PATCH', body: JSON.stringify({ defaultOriginalUrl: nextUrl })
			});
			const responseBody = await body(response);
			if (!response.ok) return setMessage(byId('default-destination-message'), responseBody.message || '기본 목적지를 저장할 수 없습니다.', true);
			state.campaign = responseBody;
			byId('campaign-default-url').value = state.campaign.defaultOriginalUrl || '';
			updateOriginalUrlPlaceholder();
			setMessage(byId('default-destination-message'), nextUrl
				? '기본 목적지를 저장했습니다. 자체 URL이 없는 링크에 즉시 적용됩니다.'
				: '기본 목적지를 제거했습니다. 자체 URL이 없는 링크는 이동하지 않습니다.');
		});
	});

	function updateOriginalUrlPlaceholder() {
		byId('campaign-original-url').placeholder = `기본값: ${state.campaign.defaultOriginalUrl || '없음'}`;
	}

	async function loadTemplates() {
		const response = await request(`${base}/api/web/projects/${state.projectId}/utm-templates`);
		if (!response.ok) return;
		state.allTemplates = await response.json();
		const options = [element('option', '', '템플릿 없음')];
		options[0].value = '';
		for (const template of state.allTemplates) {
			const option = element('option', '', template.name);
			option.value = String(template.id);
			options.push(option);
		}
		replaceChildren(byId('template-picker'), options);
		byId('template-picker').value = state.campaign.utmTemplateId ? String(state.campaign.utmTemplateId) : '';
	}

	async function refreshTemplateSelection() {
		const templateId = state.campaign.utmTemplateId;
		if (!templateId) {
			state.activeFields = [];
			state.utmDefaults = {};
			byId('template-fields-panel').hidden = true;
			byId('defaults-panel').hidden = true;
			renderUtmInputs();
			return;
		}
		const template = state.allTemplates.find((candidate) => candidate.id === templateId);
		state.activeFields = template ? template.activeFields : [];
		byId('template-fields-panel').hidden = false;
		renderTemplateFields();
		await loadDefaults();
		renderUtmInputs();
	}

	function renderTemplateFields() {
		const rows = state.activeFields.map((field) => element('code', 'campaign-template-field', field.name));
		replaceChildren(byId('template-field-list'), rows.length ? rows : [element('p', 'help-text', '활성 필드가 없습니다.')]);
	}

	byId('template-picker').addEventListener('change', async (event) => {
		const value = event.target.value;
		if (!(await confirmAction({
			title: 'UTM 템플릿을 바꿀까요?',
			body: '새 필드 구성이 이 캠페인의 기존 링크와 통계에 바로 반영됩니다. 이미 입력한 값은 그대로 유지됩니다.',
			confirmLabel: '템플릿 변경',
			danger: false
		}))) {
			event.target.value = state.campaign.utmTemplateId ? String(state.campaign.utmTemplateId) : '';
			return;
		}
		const response = await request(`${base}/api/web/campaigns/${state.campaignId}/utm-template`, {
			method: 'PATCH', body: JSON.stringify({ utmTemplateId: value ? Number(value) : null })
		});
		if (!response.ok) {
			setMessage(byId('template-message'), (await body(response)).message || '템플릿을 변경할 수 없습니다.', true);
			return;
		}
		state.campaign = await response.json();
		setMessage(byId('template-message'), '템플릿을 변경했습니다. 기존 링크와 통계에도 즉시 적용됩니다.');
		await refreshTemplateSelection();
		await loadLinks(null);
	});


	async function loadDefaults() {
		const response = await request(`${base}/api/web/campaigns/${state.campaignId}/utm-defaults`);
		if (!response.ok) {
			state.utmDefaults = {};
			return;
		}
		const defaults = await response.json();
		state.utmDefaults = defaults;
		byId('defaults-panel').hidden = state.activeFields.length === 0;
		const rows = state.activeFields.map((field) => {
			const label = element('label', 'field');
			label.append(element('span', 'field-label', field.name));
			const input = element('input', 'field-input');
			input.name = field.name;
			input.maxLength = 500;
			input.value = defaults[field.name] || '';
			input.dataset.fieldName = field.name;
			label.append(input);
			return label;
		});
		replaceChildren(byId('defaults-form'), rows);
	}

	byId('save-defaults-button').addEventListener('click', async () => {
		const defaults = {};
		byId('defaults-form').querySelectorAll('input').forEach((input) => {
			defaults[input.dataset.fieldName] = input.value.trim() === '' ? null : input.value.trim();
		});
		const response = await request(`${base}/api/web/campaigns/${state.campaignId}/utm-defaults`, { method: 'PATCH', body: JSON.stringify({ defaults }) });
		if (!response.ok) return setMessage(byId('defaults-message'), (await body(response)).message || '기본값을 저장할 수 없습니다.', true);
		state.utmDefaults = await response.json();
		setMessage(byId('defaults-message'), '기본값을 저장했습니다. 링크에서 직접 지정하지 않은 필드에 즉시 적용됩니다.');
		renderUtmInputs();
		await loadLinks(null);
	});

	function renderUtmInputs() {
		const rows = state.activeFields.map((field) => {
			const label = element('label', 'field');
			label.append(element('span', 'field-label', field.name));
			const input = element('input', 'field-input');
			input.name = field.name;
			input.maxLength = 500;
			input.placeholder = `기본값: ${state.utmDefaults[field.name] || '없음'}`;
			input.dataset.fieldName = field.name;
			input.addEventListener('input', renderLinkPreview);
			label.append(input);
			return label;
		});
		replaceChildren(byId('campaign-utm-inputs'), rows);
		renderLinkPreview();
	}

	/**
	 * 링크를 만들기 전에 실제 이동 주소를 보여준다.
	 * 목적지는 입력한 원본 URL, 없으면 캠페인 기본 목적지를 쓴다.
	 * UTM 은 입력값이 우선이고 비워 두면 캠페인 기본값이 붙는다 — 서버의 계산과 같은 규칙이다.
	 */
	function effectiveUtmValues() {
		const values = {};
		for (const field of state.activeFields) {
			const input = byId('campaign-utm-inputs').querySelector(`[data-field-name="${field.name}"]`);
			const typed = input?.value.trim();
			const value = typed || state.utmDefaults[field.name];
			if (value) values[field.name] = value;
		}
		return values;
	}

	function renderLinkPreview() {
		const panel = byId('campaign-link-preview');
		const output = byId('campaign-link-preview-url');
		const destination = byId('campaign-original-url').value.trim() || state.campaign?.defaultOriginalUrl;
		if (!destination) {
			output.textContent = '목적지가 없어 이동하지 않습니다. 링크 URL이나 캠페인 기본 목적지를 설정하세요.';
			panel.hidden = false;
			return;
		}
		const values = effectiveUtmValues();
		try {
			const url = new URL(destination);
			Object.entries(values).forEach(([name, value]) => url.searchParams.set(name, value));
			output.textContent = url.toString();
		} catch (_) {
			// 아직 URL 형태가 아니면 조립하지 않고 입력한 값을 그대로 보여준다.
			output.textContent = destination;
		}
		panel.hidden = false;
	}

	byId('campaign-original-url').addEventListener('input', renderLinkPreview);

	byId('create-campaign-link-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const form = event.target;
		const data = new FormData(form);
		const utmValues = {};
		byId('campaign-utm-inputs').querySelectorAll('input').forEach((input) => {
			if (input.value.trim() !== '') utmValues[input.dataset.fieldName] = input.value.trim();
		});
		const button = byId('create-campaign-link-button');
		button.disabled = true;
		setMessage(byId('create-campaign-link-message'), '링크를 만들고 있습니다...');
		try {
			const response = await request(`${base}/api/web/campaigns/${state.campaignId}/links`, {
				method: 'POST',
				body: JSON.stringify({
					name: data.get('name')?.trim() || null,
					originalUrl: data.get('originalUrl')?.trim() || null,
					externalId: data.get('externalId')?.trim() || null,
					utmValues
				})
			});
			const responseBody = await body(response);
			if (!response.ok) {
				setMessage(byId('create-campaign-link-message'), responseBody.message || '링크를 만들 수 없습니다.', true);
				return;
			}
			const noDestination = !data.get('originalUrl')?.trim() && !state.campaign?.defaultOriginalUrl;
			form.reset();
			renderLinkPreview();
			setMessage(byId('create-campaign-link-message'), noDestination
				? `단축 링크(${responseBody.code})를 만들었습니다. 아직 목적지가 없어 이동하지 않습니다 — 캠페인 기본 목적지나 링크 URL을 설정하세요.`
				: `단축 링크(${responseBody.code})를 만들었습니다.`);
			await loadLinks(null);
		} finally {
			button.disabled = false;
		}
	});

	async function loadLinks(cursor) {
		const url = new URL(`${base}/api/web/campaigns/${state.campaignId}/links`, location.origin);
		url.searchParams.set('limit', '20');
		if (cursor) url.searchParams.set('cursor', String(cursor));
		const response = await request(url.pathname + url.search);
		if (!response.ok) return;
		const page = await response.json();
		const items = page.items || [];
		if (!cursor) {
			state.selectedLinkCodes.clear();
			replaceChildren(byId('campaign-link-list'), items.map(campaignLinkRows));
		} else {
			byId('campaign-link-list').append(...items.map(campaignLinkRows));
		}
		const renderedCount = byId('campaign-link-list').querySelectorAll('.campaign-link-row').length;
		byId('campaign-link-count').textContent = String(renderedCount);
		byId('campaign-link-table-wrap').hidden = renderedCount === 0;
		byId('campaign-link-empty').hidden = renderedCount !== 0;
		state.linksCursor = page.nextCursor;
		byId('load-more-links-button').hidden = !page.nextCursor;
		updateLinkSelectionControls();
	}

	function campaignLinkRows(link) {
		const values = link.effectiveUtmValues || [];
		const row = element('tr', 'campaign-link-row');
		row.dataset.code = link.code;

		const selectCell = tableCell('선택', 'campaign-link-check-column');
		const checkbox = element('input', 'campaign-link-checkbox');
		checkbox.type = 'checkbox';
		checkbox.value = link.code;
		checkbox.setAttribute('aria-label', `${link.code} 링크 선택`);
		checkbox.addEventListener('change', () => {
			if (checkbox.checked) state.selectedLinkCodes.add(link.code);
			else state.selectedLinkCodes.delete(link.code);
			updateLinkSelectionControls();
		});
		selectCell.append(checkbox);

		const nameCell = tableCell('이름', '', link.name || '이름없음');
		const codeCell = tableCell('단축 코드');
		const codeButton = element('button', 'campaign-link-code', link.code);
		codeButton.type = 'button';
		codeButton.addEventListener('click', () => openLinkDetail(link.code));
		codeCell.append(codeButton);

		// 캠페인 기본 목적지가 없으면 이 링크는 실제로 이동하지 않는다.
		// 그런데도 "캠페인 기본 목적지 사용" 으로 표시하면 동작하는 링크로 오해하게 된다.
		const destinationCell = tableCell('목적지', 'campaign-link-destination',
			link.originalUrl
				|| (state.campaign?.defaultOriginalUrl ? '캠페인 기본 목적지 사용' : '목적지 없음 · 이동하지 않음'));
		if (!link.originalUrl && !state.campaign?.defaultOriginalUrl) destinationCell.classList.add('is-missing');
		const externalIdCell = tableCell('우리 쪽 식별자', '', link.externalId || '없음');
		const createdAtCell = tableCell('생성일', '', compactDate(link.createdAt));
		createdAtCell.title = formatDate(link.createdAt);

		const utmCell = tableCell('UTM');
		const utmBadge = element('button', 'campaign-utm-toggle', values.length ? `${values.length}개` : '없음');
		utmBadge.type = 'button';
		utmBadge.disabled = values.length === 0;
		if (values.length) {
			utmBadge.addEventListener('mouseenter', () => showUtmPopover(utmBadge, values));
			utmBadge.addEventListener('mouseleave', hideUtmPopover);
			utmBadge.addEventListener('focus', () => showUtmPopover(utmBadge, values));
			utmBadge.addEventListener('blur', hideUtmPopover);
		}
		utmCell.append(utmBadge);

		const actionCell = tableCell('복사', 'campaign-link-action');
		const copyButton = element('button', 'copy-button', '복사');
		copyButton.type = 'button';
		copyButton.addEventListener('click', () => copyLink(shortUrl(link.code), copyButton));
		actionCell.append(copyButton);

		// campaigns.html 의 thead 순서와 일치해야 한다.
		row.append(selectCell, nameCell, codeCell, actionCell, utmCell, destinationCell, externalIdCell, createdAtCell);
		return row;
	}

	function tableCell(label, className = '', text) {
		const cell = element('td', className, text);
		cell.dataset.label = label;
		return cell;
	}

	let utmPopover = null;

	function utmPopoverElement() {
		if (!utmPopover) {
			utmPopover = element('div', 'campaign-link-utm-panel');
			document.body.append(utmPopover);
		}
		return utmPopover;
	}

	function showUtmPopover(anchor, values) {
		const popover = utmPopoverElement();
		const heading = element('div', 'campaign-link-utm-heading');
		heading.append(element('strong', '', '현재 적용 예정 UTM'), element('span', 'status-badge', `${values.length}개`));
		const list = element('div', 'campaign-utm-value-list');
		list.append(...values.map(utmValueRow));
		const note = element('p', 'help-text', '캠페인 기본값 변경 시 링크 개별값이 없는 필드는 즉시 바뀝니다.');
		replaceChildren(popover, [heading, list, note]);
		popover.classList.add('visible');
		const rect = anchor.getBoundingClientRect();
		popover.style.top = `${rect.bottom + 6}px`;
		popover.style.left = `${Math.max(8, Math.min(rect.left, window.innerWidth - popover.offsetWidth - 8))}px`;
	}

	function hideUtmPopover() {
		if (utmPopover) utmPopover.classList.remove('visible');
	}

	function utmValueRow(value) {
		const row = element('div', 'campaign-utm-value-row');
		const name = element('code', 'campaign-utm-name', value.name);
		const content = element('span', 'campaign-utm-value', value.value);
		const inherited = value.source === 'CAMPAIGN_DEFAULT';
		const sourceLabel = value.source === 'INPUT' ? '개별 입력' : value.source === 'LINK' ? '링크 개별값' : '캠페인 기본값';
		const source = element('span', `campaign-utm-source ${inherited ? 'campaign-default' : 'link-value'}`, sourceLabel);
		row.append(name, content, source);
		return row;
	}

	function managementUrl(code) {
		return `${base}/projects?projectId=${state.projectId}&campaignId=${state.campaignId}&linkCode=${encodeURIComponent(code)}`;
	}
	function openLinkDetail(code) {
		location.href = managementUrl(code);
	}

	async function loadProjectDomain() {
		// srrrg-projects.js 도 같은 값을 쓴다. 서브도메인은 이 화면에서 바뀌지 않으므로
		// 둘 중 먼저 부른 쪽의 응답을 공유해 중복 요청을 없앤다.
		const response = await requestShared(`${base}/api/web/projects/${state.projectId}/subdomain`);
		if (!response.ok) return;
		const config = await body(response);
		state.subdomain = config.subdomain;
		state.subdomainEnabled = config.enabled;
	}

	function projectOrigin(subdomain = state.subdomainEnabled ? state.subdomain : null) {
		const port = location.port ? `:${location.port}` : '';
		return `${location.protocol}//${subdomain ? `${subdomain}.` : ''}${location.hostname}${port}`;
	}

	function shortUrl(code) {
		return `${projectOrigin()}/${encodeURIComponent(code)}`;
	}

	async function copyLink(text, button) {
		try {
			await navigator.clipboard.writeText(text);
			const label = button.textContent;
			button.textContent = '복사됨';
			setTimeout(() => button.textContent = label, 1500);
		} catch (_) {
			setMessage(campaignsMessage, '클립보드에 복사할 수 없습니다.', true);
		}
	}

	function updateLinkSelectionControls() {
		const checkboxes = Array.from(byId('campaign-link-list').querySelectorAll('.campaign-link-checkbox'));
		const checkedCount = checkboxes.filter((checkbox) => checkbox.checked).length;
		const selectAll = byId('campaign-link-select-all');
		selectAll.disabled = checkboxes.length === 0;
		selectAll.checked = checkboxes.length > 0 && checkedCount === checkboxes.length;
		selectAll.indeterminate = checkedCount > 0 && checkedCount < checkboxes.length;
		byId('campaign-link-selected-count').textContent = `${state.selectedLinkCodes.size}개 선택`;
		byId('delete-selected-links-button').disabled = state.selectedLinkCodes.size === 0;
	}

	byId('campaign-link-select-all').addEventListener('change', (event) => {
		byId('campaign-link-list').querySelectorAll('.campaign-link-checkbox').forEach((checkbox) => {
			checkbox.checked = event.target.checked;
			if (checkbox.checked) state.selectedLinkCodes.add(checkbox.value);
			else state.selectedLinkCodes.delete(checkbox.value);
		});
		updateLinkSelectionControls();
	});

	byId('delete-selected-links-button').addEventListener('click', async () => {
		const codes = Array.from(state.selectedLinkCodes);
		if (!codes.length || !(await confirmAction({
			title: `링크 ${codes.length}개를 삭제할까요?`,
			body: '삭제한 링크는 더 이상 이동하지 않습니다. 이미 배포한 주소가 있다면 함께 정리해 주세요.'
		}))) return;
		const button = byId('delete-selected-links-button');
		button.disabled = true;
		setMessage(byId('campaign-links-message'), '선택한 링크를 삭제하고 있습니다...');
		try {
			const response = await request(`${base}/api/web/campaigns/${state.campaignId}/links`, {
				method: 'DELETE', body: JSON.stringify({ codes })
			});
			const responseBody = await body(response);
			if (!response.ok) {
				setMessage(byId('campaign-links-message'), responseBody.message || '선택한 링크를 삭제할 수 없습니다.', true);
				return;
			}
			await loadLinks(null);
			setMessage(byId('campaign-links-message'), `링크 ${responseBody.deletedCount}개를 삭제했습니다.`);
		} catch (_) {
			setMessage(byId('campaign-links-message'), '네트워크 오류로 선택한 링크를 삭제하지 못했습니다.', true);
		} finally {
			updateLinkSelectionControls();
		}
	});

	byId('load-more-links-button').addEventListener('click', () => loadLinks(state.linksCursor));

	function resetImportPanel() {
		byId('import-status-panel').hidden = true;
		byId('download-errors-link').hidden = true;
		if (state.importPollHandle) clearTimeout(state.importPollHandle);
	}

	// 요청 식별자는 HTTP 프로토콜 세부사항이라 사용자가 지어낼 값이 아니다.
	// 업로드마다 새로 발급하고, 실패 후 재시도는 같은 값으로 보내 중복 생성을 막는다.
	function issueIdempotencyKey() {
		byId('csv-idempotency-key').value = crypto.randomUUID();
	}
	issueIdempotencyKey();

	byId('csv-upload-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const form = event.target;
		const data = new FormData(form);
		const file = data.get('file');
		const idempotencyKey = data.get('idempotencyKey')?.trim();
		if (!file || !file.size) return setMessage(byId('csv-message'), 'CSV 파일을 선택하세요.', true);
		setMessage(byId('csv-message'), '업로드하고 있습니다...');
		const uploadForm = new FormData();
		uploadForm.append('file', file);
		const response = await request(`${base}/api/web/campaigns/${state.campaignId}/imports/csv`, {
			method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: uploadForm
		});
		const responseBody = await body(response);
		if (!response.ok) return setMessage(byId('csv-message'), responseBody.message || 'CSV를 업로드할 수 없습니다.', true);
		setMessage(byId('csv-message'), 'CSV 업로드를 접수했습니다.');
		form.reset();
		issueIdempotencyKey();
		pollImport(responseBody.id);
	});

	// 상태 enum 을 그대로 보여주지 않는다.
	function importStatusLabel(status) {
		if (status === 'PENDING') return '대기 중입니다';
		if (status === 'PROCESSING') return '처리 중입니다';
		if (status === 'COMPLETED') return '완료했습니다';
		if (status === 'FAILED') return '실패했습니다';
		return status;
	}

	function pollImport(importId) {
		const panel = byId('import-status-panel');
		const firstReveal = panel.hidden;
		panel.hidden = false;
		// #15 업로드 결과는 사용자가 기다리는 새 내용이라 처음 나타날 때 포커스를 옮긴다.
		if (firstReveal) panel.focus();
		const check = async () => {
			const response = await request(`${base}/api/web/campaigns/${state.campaignId}/imports/${importId}`);
			if (!response.ok) return;
			const status = await response.json();
			byId('import-status-text').textContent =
				`${importStatusLabel(status.status)} · 전체 ${status.totalRows}행 · 처리 ${status.processedRows} · 성공 ${status.succeededRows} · 실패 ${status.failedRows}`;
			if (status.failedRows > 0) {
				const errorsLink = byId('download-errors-link');
				errorsLink.href = `${base}/api/web/campaigns/${state.campaignId}/imports/${importId}/errors.csv`;
				errorsLink.hidden = false;
			}
			if (status.status === 'PENDING' || status.status === 'PROCESSING') {
				state.importPollHandle = setTimeout(check, 3000);
			} else {
				loadLinks(null);
			}
		};
		check();
	}

	byId('export-links-button').addEventListener('click', () => {
		const url = new URL(`${base}/api/web/campaigns/${state.campaignId}/links.csv`, location.origin);
		const from = byId('export-created-from').value;
		const to = byId('export-created-to').value;
		const externalId = byId('export-external-id').value.trim();
		if (from) url.searchParams.set('createdFrom', new Date(from).toISOString());
		if (to) url.searchParams.set('createdTo', new Date(to).toISOString());
		if (externalId) url.searchParams.set('externalId', externalId);
		location.href = url.pathname + url.search;
	});

	byId('delete-campaign-button').addEventListener('click', async (event) => {
		// currentTarget 은 await 을 지나면 null 이 되므로 먼저 잡아둔다.
		const button = event.currentTarget;
		if (!state.campaign || !(await confirmAction({
			title: `"${state.campaign.name}" 캠페인을 삭제할까요?`,
			body: '이 캠페인에 속한 링크도 함께 삭제되어 더 이상 이동하지 않습니다.',
			confirmLabel: '캠페인 삭제'
		}))) return;
		await submitting(button, '삭제 중...', async () => {
			const response = await request(`${base}/api/web/campaigns/${state.campaignId}`, { method: 'DELETE' });
			if (!response.ok) return setMessage(campaignsMessage, (await body(response)).message || '캠페인을 삭제할 수 없습니다.', true);
			location.href = `${base}/projects?projectId=${state.projectId}`;
		});
	});

	loadCampaignPicker();
	loadProjectDomain();
})();
