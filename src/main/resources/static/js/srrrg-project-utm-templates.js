(() => {
	const app = document.querySelector('#utm-templates-app');
	if (!app) return;

	const base = SrrrgCommon.base;
	const { request, body, element, replaceChildren, setMessage } = SrrrgCommon;
	const projectId = new URLSearchParams(location.search).get('projectId');
	const byId = (id) => document.getElementById(id);
	const templatesMessage = byId('templates-message');
	const detailMessage = byId('template-detail-message');
	const state = { templates: [], selectedId: null };

	if (!projectId) {
		setMessage(templatesMessage, '프로젝트를 먼저 선택하세요.', true);
		return;
	}

	async function loadTemplates(preferredId = state.selectedId) {
		const response = await request(`${base}/api/web/projects/${projectId}/utm-templates`);
		if (!response.ok) {
			setMessage(templatesMessage, (await body(response)).message || '템플릿을 불러올 수 없습니다.', true);
			return;
		}
		state.templates = await response.json();
		renderTemplateList();
		if (!state.templates.length) {
			state.selectedId = null;
			byId('template-empty').hidden = false;
			byId('template-detail').hidden = true;
			return;
		}
		selectTemplate((state.templates.find((template) => template.id === preferredId) || state.templates[0]).id);
	}

	function renderTemplateList() {
		const options = state.templates.map((template) => {
			const option = element('option', '', `${template.name} · 필드 ${template.activeFields.length}개`);
			option.value = template.id;
			return option;
		});
		replaceChildren(byId('template-picker'), options);
		byId('template-picker').disabled = !state.templates.length;
	}

	function selectTemplate(id) {
		state.selectedId = id;
		const template = state.templates.find((candidate) => candidate.id === id);
		if (!template) return;
		byId('template-empty').hidden = true;
		byId('template-detail').hidden = false;
		byId('template-detail-name').textContent = template.name;
		byId('template-picker').value = template.id;
		setMessage(detailMessage, '');
		renderFields(template);
	}

	function renderFields(template) {
		const rows = template.activeFields.map((field) => {
			const row = element('div', 'template-field-row');
			row.append(element('span', '', field.name));
			const deleteButton = element('button', 'text-button', '삭제');
			deleteButton.type = 'button';
			deleteButton.addEventListener('click', () => deleteField(template.id, field.id));
			row.append(deleteButton);
			return row;
		});
		replaceChildren(byId('template-field-list'), rows.length ? rows : [element('p', 'help-text', '활성 필드가 없습니다.')]);
	}

	async function deleteField(templateId, fieldId) {
		if (!confirm('이 필드를 삭제하면 이 템플릿을 사용하는 모든 캠페인에서 즉시 숨겨집니다. 기존 값과 통계는 보존됩니다.')) return;
		const response = await request(`${base}/api/web/projects/${projectId}/utm-templates/${templateId}/fields/${fieldId}`, { method: 'DELETE' });
		if (!response.ok) return setMessage(detailMessage, (await body(response)).message || '필드를 삭제할 수 없습니다.', true);
		setMessage(detailMessage, '필드를 삭제했습니다.');
		await loadTemplates(templateId);
	}

	byId('create-template-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const name = new FormData(event.target).get('name')?.trim();
		if (!name) return setMessage(templatesMessage, '템플릿 이름을 입력하세요.', true);
		const response = await request(`${base}/api/web/projects/${projectId}/utm-templates`, { method: 'POST', body: JSON.stringify({ name }) });
		const responseBody = await body(response);
		if (!response.ok) return setMessage(templatesMessage, responseBody.message || '템플릿을 만들 수 없습니다.', true);
		event.target.reset();
		setMessage(templatesMessage, '템플릿을 만들었습니다.');
		await loadTemplates(responseBody.id);
	});

	byId('template-picker').addEventListener('change', (event) => {
		const template = state.templates.find(({ id }) => String(id) === event.target.value);
		if (template) selectTemplate(template.id);
	});

	byId('add-field-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		if (!state.selectedId) return;
		const name = new FormData(event.target).get('name')?.trim();
		if (!name) return setMessage(detailMessage, '필드 이름을 입력하세요.', true);
		const response = await request(`${base}/api/web/projects/${projectId}/utm-templates/${state.selectedId}/fields`, { method: 'POST', body: JSON.stringify({ name }) });
		if (!response.ok) return setMessage(detailMessage, (await body(response)).message || '필드를 추가할 수 없습니다.', true);
		event.target.reset();
		setMessage(detailMessage, '필드를 추가했습니다.');
		await loadTemplates(state.selectedId);
	});

	loadTemplates();
})();
