const steps = [
  {
    id: 1,
    label: "민원 접수",
    eyebrow: "POST",
    description: "민원 원문을 eGovFrame API로 접수하고 PostgreSQL에 저장합니다.",
    detail: "POST /api/complaints",
  },
  {
    id: 2,
    label: "원문 분석",
    eyebrow: "ANALYZE",
    description: "저장된 민원에 대해 Mock 분석 서비스를 실행합니다.",
    detail: "GET /api/complaints/{id}/analysis",
  },
  {
    id: 3,
    label: "유형 분류",
    eyebrow: "CLASSIFY",
    description: "민원 유형, 긴급도, 감정 상태, 담당 부서를 확인합니다.",
    detail: "ComplaintAnalysisResponse",
  },
  {
    id: 4,
    label: "RAG 근거",
    eyebrow: "RAG",
    description: "PostgreSQL 기반 지식문서 검색 결과를 공문 근거로 연결합니다.",
    detail: "GET /api/complaints/{id}/rag-contexts",
  },
  {
    id: 5,
    label: "공문 초안",
    eyebrow: "DRAFT",
    description: "분석 결과와 RAG 근거를 바탕으로 답변 공문 초안을 생성합니다.",
    detail: "GET /api/complaints/{id}/draft",
  },
];

const STEP_HOLD_MS = 6000;

const labels = {
  complaintType: {
    ILLEGAL_DUMPING: "무단투기 및 생활폐기물",
    ROAD_DAMAGE: "도로 시설물 파손",
    ILLEGAL_PARKING: "불법주정차",
    TRAFFIC_SIGN: "교통 시설물",
    NOISE: "소음",
    ENVIRONMENT: "환경 및 생활 불편",
    GENERAL: "일반 민원",
  },
  urgency: {
    LOW: "낮음",
    NORMAL: "보통",
    HIGH: "높음",
    EMERGENCY: "긴급",
  },
  sentiment: {
    NEUTRAL: "중립",
    DISCOMFORT: "불편",
    ANGER: "불만",
    URGENT: "긴급",
  },
  status: {
    RECEIVED: "접수됨",
    ANALYZED: "분석 완료",
    DRAFT_GENERATED: "초안 생성 완료",
    COMPLETED: "완료",
    CLOSED: "종결",
    DRAFT: "초안",
    REVISED: "수정됨",
  },
  sourceChannel: {
    WEB: "웹",
    MOBILE: "모바일",
    CALL_CENTER: "콜센터",
  },
  department: {
    RESOURCE_RECYCLING: "자원순환과",
    ROAD: "도로관리과",
    TRAFFIC: "교통행정과",
    CIVIL_AFFAIRS: "민원행정과",
  },
  documentType: {
    LAW: "법령",
    ORDINANCE: "조례",
    MANUAL: "업무 매뉴얼",
  },
  legalBasis: {
    "Waste Management Act": "폐기물관리법",
    "Road Act": "도로법",
    "Civil complaint response manual": "민원 응대 매뉴얼",
    "Local waste management ordinance": "지방자치단체 폐기물 관리 조례",
    "relevant civil complaint handling standards": "관련 민원 처리 기준",
  },
  ragTitle: {
    "Waste Management Act handling basis": "폐기물관리법 처리 근거",
    "Local waste handling ordinance sample": "지역 폐기물 처리 조례 예시",
    "Illegal dumping civil complaint response manual": "무단투기 민원 응대 매뉴얼",
  },
};

const state = {
  activeStep: 1,
  completedStep: 0,
  selectedStep: 1,
  complaint: null,
  analysis: null,
  draft: null,
  ragContexts: [],
  isProcessing: false,
};

const els = {
  stepList: document.querySelector("#stepList"),
  progressBar: document.querySelector("#progressBar"),
  inputPanel: document.querySelector("#inputPanel"),
  processingPanel: document.querySelector("#processingPanel"),
  resultPanel: document.querySelector("#resultPanel"),
  errorPanel: document.querySelector("#errorPanel"),
  activeStepNumber: document.querySelector("#activeStepNumber"),
  activeStepTitle: document.querySelector("#activeStepTitle"),
  activeStepDescription: document.querySelector("#activeStepDescription"),
  runningDetail: document.querySelector("#runningDetail"),
  stepDetailCard: document.querySelector("#stepDetailCard"),
  rawText: document.querySelector("#rawText"),
  locationText: document.querySelector("#locationText"),
  sourceChannel: document.querySelector("#sourceChannel"),
  apiKey: document.querySelector("#apiKey"),
  processButton: document.querySelector("#processButton"),
  resetButton: document.querySelector("#resetButton"),
  serverDot: document.querySelector("#serverDot"),
  serverStatus: document.querySelector("#serverStatus"),
  receiptNumber: document.querySelector("#receiptNumber"),
  complaintType: document.querySelector("#complaintType"),
  department: document.querySelector("#department"),
  urgency: document.querySelector("#urgency"),
  complaintStatus: document.querySelector("#complaintStatus"),
  sourceChannelResult: document.querySelector("#sourceChannelResult"),
  complaintSummary: document.querySelector("#complaintSummary"),
  analysisJson: document.querySelector("#analysisJson"),
  ragList: document.querySelector("#ragList"),
  draftText: document.querySelector("#draftText"),
  detailLink: document.querySelector("#detailLink"),
};

function renderSteps() {
  els.stepList.innerHTML = steps
    .map((step) => {
      const isActive = step.id === state.activeStep;
      const isComplete = step.id <= state.completedStep;
      const classes = ["step-item", isActive ? "is-active" : "", isComplete ? "is-complete" : ""]
        .filter(Boolean)
        .join(" ");

      return `
        <li class="${classes}">
          <button class="step-button" type="button" data-step-id="${step.id}" ${canSelectStep(step.id) ? "" : "disabled"}>
          <div class="node">${String(step.id).padStart(2, "0")}</div>
          <div class="step-label">${step.label}</div>
          <div class="step-kicker">${step.eyebrow}</div>
          </button>
        </li>
      `;
    })
    .join("");

  els.stepList.querySelectorAll(".step-button").forEach((button) => {
    button.addEventListener("click", () => selectStep(Number(button.dataset.stepId)));
  });
}

function setActiveStep(stepId, completedStep = Math.max(0, stepId - 1)) {
  const step = steps.find((candidate) => candidate.id === stepId) || steps[0];
  state.activeStep = step.id;
  state.selectedStep = step.id;
  state.completedStep = completedStep;

  els.activeStepNumber.textContent = `단계 ${String(step.id).padStart(2, "0")}`;
  els.activeStepTitle.textContent = step.label;
  els.activeStepDescription.textContent = step.description;
  els.runningDetail.textContent = step.detail;
  els.progressBar.style.width = `${((Math.max(completedStep, step.id - 1)) / (steps.length - 1)) * 100}%`;
  renderStepDetail(step.id, state.isProcessing ? "processing" : "review");
  renderSteps();
}

function showPanel(panel) {
  [els.inputPanel, els.processingPanel, els.resultPanel].forEach((candidate) => {
    candidate.classList.toggle("is-hidden", candidate !== panel);
  });
}

function setError(message) {
  els.errorPanel.textContent = message;
  els.errorPanel.classList.toggle("is-hidden", !message);
}

function sleep(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function canSelectStep(stepId) {
  if (state.isProcessing) {
    return stepId <= state.completedStep || stepId === state.activeStep;
  }

  if (state.draft) {
    return stepId <= steps.length;
  }

  return stepId === 1;
}

function apiHeaders() {
  const headers = {
    "Content-Type": "application/json",
  };
  const apiKey = els.apiKey.value.trim();
  if (apiKey) {
    headers["X-API-Key"] = apiKey;
  }
  return headers;
}

async function apiRequest(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: {
      ...apiHeaders(),
      ...(options.headers || {}),
    },
  });

  const contentType = response.headers.get("content-type") || "";
  const body = contentType.includes("application/json") ? await response.json() : await response.text();

  if (!response.ok || body.success === false) {
    const message = body?.error?.message || body?.message || `HTTP ${response.status}`;
    throw new Error(message);
  }

  return body.data;
}

async function checkServer() {
  try {
    await fetch("/actuator/health");
    els.serverDot.classList.remove("is-error");
    els.serverDot.classList.add("is-ok");
    els.serverStatus.textContent = "서버 응답 가능";
  } catch {
    els.serverDot.classList.remove("is-ok");
    els.serverDot.classList.add("is-error");
    els.serverStatus.textContent = "서버 응답 없음";
  }
}

async function runPipeline() {
  const rawText = els.rawText.value.trim();
  const locationText = els.locationText.value.trim();

  if (!rawText) {
    setError("민원 원문을 입력해야 분석을 시작할 수 있습니다.");
    return;
  }

  state.isProcessing = true;
  els.processButton.disabled = true;
  setError("");
  showPanel(els.processingPanel);

  try {
    setActiveStep(1, 0);
    state.complaint = await apiRequest("/api/complaints", {
      method: "POST",
      body: JSON.stringify({
        sourceChannel: els.sourceChannel.value,
        rawText,
        locationText,
      }),
    });

    await sleep(STEP_HOLD_MS);
    setActiveStep(2, 1);
    state.analysis = await apiRequest(`/api/complaints/${state.complaint.id}/analysis`);

    await sleep(STEP_HOLD_MS);
    setActiveStep(3, 2);
    await sleep(STEP_HOLD_MS);

    setActiveStep(4, 3);
    state.draft = await apiRequest(`/api/complaints/${state.complaint.id}/draft`);
    state.ragContexts = await apiRequest(`/api/complaints/${state.complaint.id}/rag-contexts`);

    await sleep(STEP_HOLD_MS);
    setActiveStep(5, 5);
    els.progressBar.style.width = "100%";
    renderResult();
    showPanel(els.resultPanel);
  } catch (error) {
    setError(`서버 연동 실패: ${error.message}`);
    showPanel(els.inputPanel);
  } finally {
    state.isProcessing = false;
    els.processButton.disabled = false;
  }
}

function renderResult() {
  const complaint = state.complaint || {};
  const analysis = state.analysis || {};
  const draft = state.draft || {};
  const references = state.ragContexts?.length ? state.ragContexts : draft.references || [];

  els.receiptNumber.textContent = complaint.receiptNumber || complaint.id || "-";
  els.complaintType.textContent = translate("complaintType", analysis.complaintType);
  els.department.textContent = analysis.department
    ? `${translate("department", analysis.departmentCode)} (${analysis.departmentCode || "-"})`
    : "-";
  els.urgency.textContent = translate("urgency", analysis.urgency);
  els.complaintStatus.textContent = translate("status", complaint.status || draft.status);
  els.sourceChannelResult.textContent = translate("sourceChannel", complaint.sourceChannel);
  els.complaintSummary.textContent = complaint.rawText || "-";
  els.analysisJson.textContent = formatAnalysisJson(analysis);
  els.draftText.textContent = translateDraft(draft.draftText, analysis);
  els.detailLink.href = complaint.id ? `/api/complaints/${complaint.id}` : "#";

  if (!references.length) {
    els.ragList.innerHTML = "<li>조회된 RAG 근거 문서가 없습니다.</li>";
    return;
  }

  els.ragList.innerHTML = references
    .map((item) => {
      const score = Number.isFinite(item.score) ? item.score.toFixed(2) : "-";
      return `
        <li>
          <strong>${escapeHtml(item.title || "근거 문서")}</strong>
          <p>${escapeHtml(translate("legalBasis", item.legalBasis) || translate("documentType", item.documentType) || "근거 정보 없음")} · 유사도 ${score}</p>
          <p>${escapeHtml(translateSnippet(item.contentSnippet || ""))}</p>
        </li>
      `;
    })
    .join("");
}

function formatAnalysisJson(analysis) {
  const view = {
    intent: translateIntent(analysis.intent),
    complaintType: translate("complaintType", analysis.complaintType),
    urgency: translate("urgency", analysis.urgency),
    sentiment: translate("sentiment", analysis.sentiment),
    department: translate("department", analysis.departmentCode),
    departmentCode: analysis.departmentCode || "-",
    locationText: analysis.locationText || "-",
  };

  if (!analysis.analysisJson) {
    return JSON.stringify(view, null, 2);
  }

  try {
    const parsed = JSON.parse(analysis.analysisJson);
    return JSON.stringify(
      {
        intent: translateIntent(parsed.intent || analysis.intent),
        urgency: translate("urgency", parsed.urgency || analysis.urgency),
        sentiment: translate("sentiment", parsed.sentiment || analysis.sentiment),
        department: translate("department", analysis.departmentCode),
        keywords: translateKeywords(parsed.keywords || []),
        requiredAction: translateAction(parsed.requiredAction),
      },
      null,
      2,
    );
  } catch {
    return JSON.stringify(view, null, 2);
  }
}

function renderStepDetail(stepId, mode = "review") {
  const detail = buildStepDetail(stepId, mode);
  els.stepDetailCard.innerHTML = detail;
  els.stepDetailCard.classList.toggle("is-hidden", !detail);
}

function buildStepDetail(stepId, mode) {
  const complaint = state.complaint;
  const analysis = state.analysis;
  const draft = state.draft;
  const references = state.ragContexts?.length ? state.ragContexts : draft?.references || [];

  if (stepId === 1) {
    if (!complaint) {
      return mode === "processing"
        ? "<h3>실행 내용</h3><p>입력된 민원 원문을 JSON 요청으로 구성해 서버에 접수합니다.</p>"
        : "";
    }

    return `
      <h3>접수 결과</h3>
      <dl>
        <div><dt>접수번호</dt><dd>${escapeHtml(complaint.receiptNumber || "-")}</dd></div>
        <div><dt>접수 채널</dt><dd>${escapeHtml(translate("sourceChannel", complaint.sourceChannel))}</dd></div>
        <div><dt>위치</dt><dd>${escapeHtml(complaint.locationText || "-")}</dd></div>
      </dl>
      <p>${escapeHtml(complaint.rawText || "")}</p>
    `;
  }

  if (stepId === 2) {
    return `
      <h3>원문 분석 방식</h3>
      <p>현재 개발 기본값은 외부 AI/AWS 호출 없이 Mock 분석 클라이언트와 PostgreSQL 저장소를 사용합니다.</p>
      <p>${analysis ? `분석 결과는 ${escapeHtml(translateIntent(analysis.intent))}로 저장되었습니다.` : "서버 분석 응답을 기다리는 중입니다."}</p>
    `;
  }

  if (stepId === 3) {
    if (!analysis) {
      return "<h3>유형 분류</h3><p>분석 응답을 받은 뒤 민원 유형, 긴급도, 감정 상태, 담당 부서를 표시합니다.</p>";
    }

    return `
      <h3>분류 결과</h3>
      <dl>
        <div><dt>민원 유형</dt><dd>${escapeHtml(translate("complaintType", analysis.complaintType))}</dd></div>
        <div><dt>긴급도</dt><dd>${escapeHtml(translate("urgency", analysis.urgency))}</dd></div>
        <div><dt>감정 상태</dt><dd>${escapeHtml(translate("sentiment", analysis.sentiment))}</dd></div>
        <div><dt>담당 부서</dt><dd>${escapeHtml(translate("department", analysis.departmentCode))}</dd></div>
      </dl>
    `;
  }

  if (stepId === 4) {
    if (!references.length) {
      return "<h3>RAG 근거 검색</h3><p>공문 초안 생성 과정에서 PostgreSQL 지식문서 검색 결과를 연결합니다.</p>";
    }

    return `
      <h3>RAG 근거 검색 결과</h3>
      <ul>
        ${references
          .map(
            (item) => `
              <li>
                <strong>${escapeHtml(translate("ragTitle", item.title))}</strong>
                <p>${escapeHtml(translate("legalBasis", item.legalBasis) || translate("documentType", item.documentType))}</p>
              </li>
            `,
          )
          .join("")}
      </ul>
    `;
  }

  if (stepId === 5) {
    return `
      <h3>공문 초안</h3>
      <p>${escapeHtml(draft?.draftText ? translateDraft(draft.draftText, analysis) : "분석 결과와 RAG 근거를 바탕으로 초안을 생성하는 중입니다.")}</p>
    `;
  }

  return "";
}

function selectStep(stepId) {
  if (!canSelectStep(stepId)) {
    return;
  }

  if (state.draft && stepId === 5) {
    state.selectedStep = stepId;
    state.activeStep = stepId;
    renderSteps();
    renderResult();
    showPanel(els.resultPanel);
    return;
  }

  state.selectedStep = stepId;
  state.activeStep = stepId;
  const step = steps.find((candidate) => candidate.id === stepId) || steps[0];
  els.activeStepNumber.textContent = `단계 ${String(step.id).padStart(2, "0")}`;
  els.activeStepTitle.textContent = step.label;
  els.activeStepDescription.textContent = step.description;
  els.runningDetail.textContent = state.draft ? "완료된 단계 다시 보기" : step.detail;
  renderStepDetail(stepId, state.draft ? "review" : "processing");
  showPanel(els.processingPanel);
  renderSteps();
}

function translate(group, value) {
  if (!value) {
    return "-";
  }

  return labels[group]?.[value] || value;
}

function translateIntent(value) {
  const intents = {
    "Waste dumping report": "무단투기 신고",
    "Road facility complaint": "도로 시설 민원",
    "Traffic sign complaint": "교통표지 민원",
    "General civil complaint": "일반 민원",
  };

  return intents[value] || value || "-";
}

function translateKeywords(keywords) {
  const dictionary = {
    waste: "폐기물",
    dumping: "무단투기",
    "civil complaint": "민원",
    road: "도로",
    pothole: "포트홀",
    street: "도로",
  };

  return keywords.map((keyword) => dictionary[keyword] || keyword);
}

function translateAction(value) {
  const actions = {
    "site inspection and removal review": "현장 확인 및 조치 검토",
  };

  return actions[value] || value || "-";
}

function translateSnippet(value) {
  return value
    .replaceAll("Household waste and illegal dumping complaints require site confirmation, removal review and enforcement review according to waste handling standards.", "생활폐기물 및 무단투기 민원은 폐기물 처리 기준에 따라 현장 확인, 수거 검토, 행정 조치 검토가 필요합니다.")
    .replaceAll("Local governments may inspect reported dumping sites, remove household waste and guide residents according to local waste ordinances.", "지방자치단체는 조례에 따라 신고된 무단투기 장소를 확인하고 생활폐기물 수거 및 주민 안내를 수행할 수 있습니다.")
    .replaceAll("Responses should mention receipt of the complaint, responsible department, site inspection plan and additional confirmation if required.", "답변에는 민원 접수 사실, 담당 부서, 현장 확인 계획, 추가 확인 필요 여부를 포함해야 합니다.");
}

function translateDraft(value, analysis = {}) {
  if (!value) {
    return "-";
  }

  const department = translate("department", analysis?.departmentCode);
  const type = translate("complaintType", analysis?.complaintType);
  const basis = translate("legalBasis", firstLegalBasis());

  return `안녕하세요. 접수하신 민원은 "${type}" 유형으로 분류되었습니다. 담당 부서는 ${department}로 예상됩니다. 담당 부서는 제출된 내용과 위치 정보를 검토하고, 필요한 경우 현장 확인을 진행한 뒤 관련 절차에 따라 후속 조치를 안내할 예정입니다. 본 초안은 ${basis} 등을 근거로 작성되었으며, 최종 발송 전 담당자의 검토가 필요합니다.`;
}

function firstLegalBasis() {
  const references = state.ragContexts?.length ? state.ragContexts : state.draft?.references || [];
  return references.find((item) => item.legalBasis)?.legalBasis || "Civil complaint response manual";
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function reset() {
  state.activeStep = 1;
  state.completedStep = 0;
  state.selectedStep = 1;
  state.complaint = null;
  state.analysis = null;
  state.draft = null;
  state.ragContexts = [];
  setError("");
  setActiveStep(1, 0);
  els.progressBar.style.width = "0";
  showPanel(els.inputPanel);
}

els.processButton.addEventListener("click", runPipeline);
els.resetButton.addEventListener("click", reset);
els.rawText.addEventListener("input", () => {
  els.processButton.disabled = state.isProcessing || !els.rawText.value.trim();
});

renderSteps();
setActiveStep(1, 0);
els.processButton.disabled = true;
checkServer();
