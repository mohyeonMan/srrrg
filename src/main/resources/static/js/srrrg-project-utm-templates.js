(() => {
	const app = document.querySelector('#utm-templates-app');
	if (!app) return;

	const base = SrrrgCommon.base;
	const { request, body, element, replaceChildren, setMessage, submitting, confirmAction } = SrrrgCommon;
	// 주소에 projectId 가 없어도(자동 선택된 프로젝트) UTM 탭에 들어올 수 있으므로
	// 여기서 조각 전체를 return 으로 끝내면 안 된다 — 그러면 reload 조차 노출되지 않아
	// 탭이 죽는다. 프로젝트는 srrrg-projects.js 가 reload(id) 로 알려 준다.
	let projectId = new URLSearchParams(location.search).get('projectId');
	const byId = (id) => document.getElementById(id);
	const templatesMessage = byId('templates-message');
	const detailMessage = byId('template-detail-message');
	const state = { templates: [], selectedId: null };

	async function loadTemplates(preferredId = state.selectedId) {
		if (!projectId) return setMessage(templatesMessage, '프로젝트를 먼저 선택하세요.', true);
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
		// 다른 템플릿으로 옮기면 열려 있던 이름 입력칸은 닫는다(이전 이름이 남아 헷갈린다).
		openRename(false);
		setMessage(detailMessage, '');
		renderFields(template);
	}

	function renderFields(template) {
		const rows = template.activeFields.map((field) => {
			const row = element('div', 'template-field-row');
			row.append(element('span', '', field.name));
			// 텍스트 링크처럼 보이면 되돌리기 어려운 동작인지 읽히지 않는다. 버튼 면을 준다.
			const deleteButton = element('button', 'danger-button compact-action', '삭제');
			deleteButton.type = 'button';
			deleteButton.setAttribute('aria-label', `${field.name} 필드 삭제`);
			deleteButton.addEventListener('click', () => deleteField(template.id, field.id));
			row.append(deleteButton);
			return row;
		});
		replaceChildren(byId('template-field-list'), rows.length ? rows : [element('p', 'help-text', '활성 필드가 없습니다.')]);
	}

	async function deleteField(templateId, fieldId) {
		if (!(await confirmAction({
			title: '이 필드를 삭제할까요?',
			body: '이 템플릿을 쓰는 모든 캠페인에서 바로 사라집니다. 이미 발행한 링크의 값과 통계는 그대로 남습니다.'
		}))) return;
		const response = await request(`${base}/api/web/projects/${projectId}/utm-templates/${templateId}/fields/${fieldId}`, { method: 'DELETE' });
		if (!response.ok) return setMessage(detailMessage, (await body(response)).message || '필드를 삭제할 수 없습니다.', true);
		setMessage(detailMessage, '필드를 삭제했습니다.');
		await loadTemplates(templateId);
	}

	// 제목 자리를 입력칸이 대신한다. 둘을 함께 두면 같은 값이 두 군데 보인다.
	function openRename(open) {
		byId('template-name-view').hidden = open;
		byId('rename-template-form').hidden = !open;
		if (!open) return;
		const template = state.templates.find((candidate) => candidate.id === state.selectedId);
		byId('rename-template-name').value = template ? template.name : '';
		byId('rename-template-name').focus();
	}

	byId('rename-template').addEventListener('click', () => openRename(true));
	byId('cancel-rename-template').addEventListener('click', () => {
		openRename(false);
		byId('rename-template').focus();
	});

	byId('rename-template-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const name = new FormData(event.target).get('name')?.trim();
		if (!name) return setMessage(detailMessage, '템플릿 이름을 입력하세요.', true);
		const templateId = state.selectedId;
		await submitting(event.submitter, '저장 중...', async () => {
			setMessage(detailMessage, '이름을 바꾸고 있습니다...');
			const response = await request(`${base}/api/web/projects/${projectId}/utm-templates/${templateId}`,
				{ method: 'PATCH', body: JSON.stringify({ name }) });
			if (!response.ok) return setMessage(detailMessage, (await body(response)).message || '이름을 바꿀 수 없습니다.', true);
			openRename(false);
			await loadTemplates(templateId);
			setMessage(detailMessage, '템플릿 이름을 바꿨습니다.');
			byId('rename-template').focus();
		});
	});

	byId('delete-template').addEventListener('click', async () => {
		const template = state.templates.find((candidate) => candidate.id === state.selectedId);
		if (!template) return;
		if (!(await confirmAction({
			title: `"${template.name}" 템플릿을 삭제할까요?`,
			// 사용 중인 캠페인이 있으면 서버가 거부한다. 여기서는 이미 발행된 링크가 안전하다는 점만 알린다.
			body: '이미 만든 링크의 UTM 값과 통계는 그대로 남습니다. 앞으로 이 템플릿을 고를 수 없게 됩니다.',
			confirmLabel: '템플릿 삭제'
		}))) return;
		const response = await request(`${base}/api/web/projects/${projectId}/utm-templates/${template.id}`, { method: 'DELETE' });
		if (!response.ok) {
			// "사용 중인 캠페인이 있어 삭제할 수 없습니다..." 같은 서버 문구를 그대로 보여 준다.
			return setMessage(detailMessage, (await body(response)).message || '템플릿을 삭제할 수 없습니다.', true);
		}
		state.selectedId = null;
		await loadTemplates();
		setMessage(templatesMessage, '템플릿을 삭제했습니다.');
	});

	byId('create-template-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const name = new FormData(event.target).get('name')?.trim();
		if (!name) return setMessage(templatesMessage, '템플릿 이름을 입력하세요.', true);
		const form = event.target;
		await submitting(event.submitter, '만드는 중...', async () => {
			setMessage(templatesMessage, '템플릿을 만들고 있습니다...');
			const response = await request(`${base}/api/web/projects/${projectId}/utm-templates`, { method: 'POST', body: JSON.stringify({ name }) });
			const responseBody = await body(response);
			if (!response.ok) return setMessage(templatesMessage, responseBody.message || '템플릿을 만들 수 없습니다.', true);
			form.reset();
			setMessage(templatesMessage, '템플릿을 만들었습니다.');
			await loadTemplates(responseBody.id);
		});
	});

	// 템플릿이 0개인 빈 상태에서 바로 만들기로 넘어간다.
	byId('create-first-template').addEventListener('click', () => {
		const disclosure = document.querySelector('.project-create-disclosure');
		if (disclosure) disclosure.open = true;
		byId('new-template-name').focus();
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
		const form = event.target;
		await submitting(event.submitter, '추가 중...', async () => {
			setMessage(detailMessage, '필드를 추가하고 있습니다...');
			const response = await request(`${base}/api/web/projects/${projectId}/utm-templates/${state.selectedId}/fields`, { method: 'POST', body: JSON.stringify({ name }) });
			if (!response.ok) return setMessage(detailMessage, (await body(response)).message || '필드를 추가할 수 없습니다.', true);
			form.reset();
			setMessage(detailMessage, '필드를 추가했습니다.');
			await loadTemplates(state.selectedId);
		});
	});

	// 이 조각은 이제 프로젝트의 UTM 탭 패널이라 페이지를 열 때 항상 DOM 에 있다.
	// 여기서 바로 불러오면 UTM 탭을 보지 않는 사람도 매번 목록을 조회한다.
	// srrrg-projects.js 가 그 탭을 처음 열 때 reload() 로 부른다.
	window.SrrrgProjectUtmTemplates = {
		reload(newProjectId) {
			if (newProjectId) projectId = newProjectId;
			return loadTemplates();
		}
	};
})();
