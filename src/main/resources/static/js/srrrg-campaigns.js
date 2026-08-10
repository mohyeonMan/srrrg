(() => {
	const app = document.querySelector('#campaigns-app');
	if (!app) return;

	const base = document.querySelector('meta[name="context-path"]')?.content.replace(/\/$/, '') || '';
	const params = new URLSearchParams(location.search);
	const state = {
		projectId: params.get('projectId'),
		campaignId: params.get('campaignId') ? Number(params.get('campaignId')) : null,
		campaign: null,
		activeFields: [],
		utmDefaults: {},
		selectedLinkCodes: new Set(),
		linksCursor: null,
		refreshing: null,
		importPollHandle: null
	};

	const byId = (id) => document.getElementById(id);
	const campaignsMessage = byId('campaigns-message');

	function csrf() {
		return decodeURIComponent(document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)?.[1] || '');
	}

	function send(url, options = {}) {
		const headers = { Accept: 'application/json', 'X-XSRF-TOKEN': csrf(), ...(options.headers || {}) };
		if (options.body && !(options.body instanceof FormData) && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
		return fetch(url, { ...options, headers });
	}

	async function request(url, options = {}) {
		let response = await send(url, options);
		if (response.status !== 401) return response;
		if (!state.refreshing) {
			state.refreshing = send(`${base}/api/web/auth/refresh`, { method: 'POST' }).finally(() => state.refreshing = null);
		}
		if (!(await state.refreshing).ok) {
			location.href = `${base}/login?returnTo=${encodeURIComponent(location.pathname + location.search)}`;
			return response;
		}
		return send(url, options);
	}

	async function body(response) {
		try {
			return await response.json();
		} catch (_) {
			return {};
		}
	}

	function element(tag, className, text) {
		const node = document.createElement(tag);
		if (className) node.className = className;
		if (text !== undefined) node.textContent = text;
		return node;
	}

	function replaceChildren(target, children) {
		target.replaceChildren(...children);
	}

	function setMessage(target, text, error = false) {
		target.textContent = text;
		target.classList.toggle('error', error);
	}

	function formatDate(value) {
		return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
	}

	if (!state.projectId) {
		setMessage(campaignsMessage, '프로젝트를 먼저 선택하세요.', true);
		byId('back-to-project').href = `${base}/projects`;
		return;
	}
	byId('back-to-project').href = `${base}/projects?projectId=${state.projectId}`;

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
		byId('campaign-default-url').value = state.campaign.defaultOriginalUrl || '';
		byId('download-template-link').href = `${base}/api/web/campaigns/${campaignId}/links/template.csv`;
		byId('campaign-statistics-link').href = `${base}/statistics?projectId=${state.projectId}&campaignId=${campaignId}${periodSuffix()}`;

		await loadTemplates();
		await refreshTemplateSelection();
		await loadLinks(null);
		resetImportPanel();
	}

	byId('default-destination-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const nextUrl = new FormData(event.target).get('defaultOriginalUrl')?.trim() || null;
		if (state.campaign.defaultOriginalUrl && !nextUrl
				&& !confirm('기본 목적지를 제거하면 자체 URL이 없는 링크는 410 Gone을 반환합니다. 계속할까요?')) return;
		const response = await request(`${base}/api/web/campaigns/${state.campaignId}`, {
			method: 'PATCH', body: JSON.stringify({ defaultOriginalUrl: nextUrl })
		});
		const responseBody = await body(response);
		if (!response.ok) return setMessage(byId('default-destination-message'), responseBody.message || '기본 목적지를 저장할 수 없습니다.', true);
		state.campaign = responseBody;
		byId('campaign-default-url').value = state.campaign.defaultOriginalUrl || '';
		setMessage(byId('default-destination-message'), nextUrl
			? '기본 목적지를 저장했습니다. 자체 URL이 없는 링크에 즉시 적용됩니다.'
			: '기본 목적지를 제거했습니다. 자체 URL이 없는 링크는 410 Gone을 반환합니다.');
	});

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
		const rows = state.activeFields.map((field) => {
			const row = element('div', 'template-field-row');
			row.append(element('span', '', field.name));
			const deleteButton = element('button', 'text-button', '삭제');
			deleteButton.type = 'button';
			deleteButton.addEventListener('click', () => deleteField(field.id));
			row.append(deleteButton);
			return row;
		});
		replaceChildren(byId('template-field-list'), rows.length ? rows : [element('p', 'help-text', '활성 필드가 없습니다.')]);
	}

	async function deleteField(fieldId) {
		if (!confirm('이 필드를 삭제하면 이 템플릿을 사용하는 모든 캠페인에서 즉시 숨겨집니다. 기존 값과 통계는 보존됩니다.')) return;
		const templateId = state.campaign.utmTemplateId;
		const response = await request(`${base}/api/web/projects/${state.projectId}/utm-templates/${templateId}/fields/${fieldId}`, { method: 'DELETE' });
		if (!response.ok) return setMessage(byId('template-message'), (await body(response)).message || '필드를 삭제할 수 없습니다.', true);
		setMessage(byId('template-message'), '필드를 삭제했습니다.');
		await loadTemplates();
		await refreshTemplateSelection();
		await loadLinks(null);
	}

	byId('template-picker').addEventListener('change', async (event) => {
		const value = event.target.value;
		if (!confirm('템플릿을 변경하면 현재 필드 구성이 기존 링크의 리다이렉트와 통계에 즉시 적용됩니다. 계속할까요?')) {
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

	byId('reload-templates-button').addEventListener('click', async () => {
		await loadTemplates();
		await refreshTemplateSelection();
	});

	byId('create-template-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const name = new FormData(event.target).get('name')?.trim();
		if (!name) return;
		const response = await request(`${base}/api/web/projects/${state.projectId}/utm-templates`, { method: 'POST', body: JSON.stringify({ name }) });
		if (!response.ok) return setMessage(byId('template-message'), (await body(response)).message || '템플릿을 만들 수 없습니다.', true);
		event.target.reset();
		setMessage(byId('template-message'), '템플릿을 만들었습니다. 아래에서 선택하세요.');
		await loadTemplates();
	});

	byId('add-field-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const templateId = state.campaign.utmTemplateId;
		if (!templateId) return setMessage(byId('template-message'), '먼저 템플릿을 선택하세요.', true);
		const name = new FormData(event.target).get('name')?.trim();
		if (!name) return;
		if (!confirm('필드를 추가하면 이 템플릿을 사용하는 모든 캠페인에 즉시 반영됩니다. 계속할까요?')) return;
		const response = await request(`${base}/api/web/projects/${state.projectId}/utm-templates/${templateId}/fields`, { method: 'POST', body: JSON.stringify({ name }) });
		if (!response.ok) return setMessage(byId('template-message'), (await body(response)).message || '필드를 추가할 수 없습니다.', true);
		event.target.reset();
		setMessage(byId('template-message'), '필드를 추가했습니다.');
		await loadTemplates();
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
			input.placeholder = '캠페인 기본값 사용';
			input.dataset.fieldName = field.name;
			input.addEventListener('input', renderUtmPreview);
			label.append(input);
			return label;
		});
		replaceChildren(byId('campaign-utm-inputs'), rows);
		renderUtmPreview();
	}

	function renderUtmPreview() {
		const inputs = new Map(Array.from(byId('campaign-utm-inputs').querySelectorAll('input'))
			.map((input) => [input.dataset.fieldName, input.value.trim()]));
		const values = state.activeFields.flatMap((field) => {
			const linkValue = inputs.get(field.name);
			if (linkValue) return [{ name: field.name, value: linkValue, source: 'INPUT' }];
			const defaultValue = state.utmDefaults[field.name];
			return defaultValue ? [{ name: field.name, value: defaultValue, source: 'CAMPAIGN_DEFAULT' }] : [];
		});
		byId('campaign-utm-preview-count').textContent = `${values.length}개`;
		replaceChildren(byId('campaign-utm-preview-list'), values.length
			? values.map(utmValueRow)
			: [element('p', 'campaign-utm-empty', '현재 적용될 UTM이 없습니다.')]);
	}

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
			form.reset();
			renderUtmPreview();
			setMessage(byId('create-campaign-link-message'), `단축 링크(${responseBody.code})를 만들었습니다.`);
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
			replaceChildren(byId('campaign-link-list'), items.flatMap(campaignLinkRows));
		} else {
			byId('campaign-link-list').append(...items.flatMap(campaignLinkRows));
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

		const codeCell = tableCell('단축 코드');
		const codeLink = element('a', 'campaign-link-code', link.code);
		codeLink.href = managementUrl(link.code);
		codeCell.append(codeLink);

		const destinationCell = tableCell('목적지', 'campaign-link-destination',
			link.originalUrl || '캠페인 기본 목적지 사용');
		const externalIdCell = tableCell('external_id', '', link.externalId || '없음');
		const createdAtCell = tableCell('생성일', '', formatDate(link.createdAt));

		const utmCell = tableCell('UTM');
		const utmButton = element('button', 'campaign-utm-toggle', values.length ? `${values.length}개` : '없음');
		utmButton.type = 'button';
		utmButton.disabled = values.length === 0;
		utmButton.setAttribute('aria-expanded', 'false');
		utmCell.append(utmButton);

		const actionCell = tableCell('관리', 'campaign-link-action');
		actionCell.append(statisticsLink(link.code));
		row.append(selectCell, codeCell, destinationCell, externalIdCell, createdAtCell, utmCell, actionCell);

		const detailRow = element('tr', 'campaign-link-utm-detail-row');
		detailRow.hidden = true;
		const detailCell = element('td', 'campaign-link-utm-detail-cell');
		detailCell.colSpan = 7;
		detailCell.append(effectiveUtmPanel(values));
		detailRow.append(detailCell);
		utmButton.addEventListener('click', () => {
			const expanded = detailRow.hidden;
			detailRow.hidden = !expanded;
			utmButton.setAttribute('aria-expanded', String(expanded));
			row.classList.toggle('utm-expanded', expanded);
		});
		return [row, detailRow];
	}

	function tableCell(label, className = '', text) {
		const cell = element('td', className, text);
		cell.dataset.label = label;
		return cell;
	}

	function effectiveUtmPanel(values) {
		const panel = element('div', 'campaign-link-utm-panel');
		const heading = element('div', 'campaign-link-utm-heading');
		heading.append(element('strong', '', '현재 적용 예정 UTM'), element('span', 'status-badge', `${values.length}개`));
		const list = element('div', 'campaign-utm-value-list');
		list.append(...values.map(utmValueRow));
		const note = element('p', 'help-text', '캠페인 기본값 변경 시 링크 개별값이 없는 필드는 즉시 바뀝니다.');
		panel.append(heading, list, note);
		return panel;
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
		return `${base}/manage?projectId=${state.projectId}&campaignId=${state.campaignId}&code=${encodeURIComponent(code)}${periodSuffix()}`;
	}
	function periodSuffix() { return params.get('from') && params.get('to') ? `&from=${encodeURIComponent(params.get('from'))}&to=${encodeURIComponent(params.get('to'))}&bucket=${encodeURIComponent(params.get('bucket') || 'DAY')}` : ''; }

	function statisticsLink(code) { const link = element('a', '', '상세보기'); link.href = managementUrl(code); return link; }

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
		if (!codes.length || !confirm(`선택한 링크 ${codes.length}개를 삭제할까요? 삭제한 링크는 더 이상 이동하지 않습니다.`)) return;
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

	byId('csv-upload-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const form = event.target;
		const data = new FormData(form);
		const file = data.get('file');
		const idempotencyKey = data.get('idempotencyKey')?.trim();
		if (!file || !file.size) return setMessage(byId('csv-message'), 'CSV 파일을 선택하세요.', true);
		if (!idempotencyKey) return setMessage(byId('csv-message'), 'Idempotency-Key를 입력하세요.', true);
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
		pollImport(responseBody.id);
	});

	function pollImport(importId) {
		byId('import-status-panel').hidden = false;
		const check = async () => {
			const response = await request(`${base}/api/web/campaigns/${state.campaignId}/imports/${importId}`);
			if (!response.ok) return;
			const status = await response.json();
			byId('import-status-text').textContent =
				`상태: ${status.status} · 전체 ${status.totalRows} · 처리 ${status.processedRows} · 성공 ${status.succeededRows} · 실패 ${status.failedRows}`;
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

	byId('archive-campaign-button').addEventListener('click', async () => {
		if (!state.campaign || !confirm(`"${state.campaign.name}" 캠페인을 삭제할까요? 소속된 모든 링크가 함께 삭제됩니다.`)) return;
		const response = await request(`${base}/api/web/campaigns/${state.campaignId}`, { method: 'DELETE' });
		if (!response.ok) return setMessage(campaignsMessage, (await body(response)).message || '캠페인을 삭제할 수 없습니다.', true);
		location.href = `${base}/projects?projectId=${state.projectId}`;
	});

	loadCampaignPicker();
})();
