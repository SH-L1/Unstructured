const state = { detail: null, run: null };

const LABELS = {
  complaintType: {
    ILLEGAL_DUMPING: "무단투기",
    ROAD_DAMAGE: "도로 파손",
    ILLEGAL_PARKING: "불법 주정차",
    TRAFFIC_SIGN: "교통 표지",
    HAZARDOUS_MATERIAL: "위험물",
    NOISE: "소음",
    ENVIRONMENT: "환경",
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
    ANGER: "분노",
    ANXIETY: "불안",
  },
  status: {
    RECEIVED: "접수",
    TRIAGE_REVIEW: "분류 검토",
    DRAFT_REVIEW: "초안 검토",
    APPROVAL_PENDING: "승인 대기",
    ANALYZED: "분석 완료",
    DRAFT_READY: "초안 준비",
    DRAFT_GENERATED: "초안 생성",
    REVIEW_APPROVED: "검토 통과",
    APPROVED: "승인",
    COMPLETED: "완료",
    IN_PROGRESS: "진행 중",
    BLOCKED: "진행 보류",
    DRAFT: "초안",
    REVISED: "수정",
    CANDIDATE: "후보",
    HUMAN_SELECTED: "사람 선택",
    VERIFIED: "확인 완료",
    REJECTED: "반려",
    PENDING: "대기 중",
    RUNNING: "실행 중",
    SUCCEEDED: "성공",
    FAILED: "실패",
  },
  blocker: {
    NEEDS_LOCATION: "위치 확인 필요",
    NEEDS_JURISDICTION: "관할 부서 확인 필요",
    EVIDENCE_INSUFFICIENT: "공식 근거 부족",
    CONFLICT_DETECTED: "근거 충돌",
    PROCESSING_FAILED: "처리 실패",
  },
  department: {
    RESOURCE_RECYCLING: "자원순환과",
    ROAD: "도로관리과",
    TRAFFIC: "교통행정과",
    CIVIL_AFFAIRS: "민원행정과",
    SAFETY_CONTROL: "안전관리과",
    ENVIRONMENT: "환경보전과",
    BUILDING_HOUSING: "건축주택과",
    PARK_GREEN: "공원녹지과",
    WATER_SEWER: "상하수도과",
    HEALTH_SANITATION: "보건위생과",
    ANIMAL_LIVESTOCK: "축산과",
    URBAN_MANAGEMENT: "도시관리과",
    WELFARE: "복지정책과",
  },
  claimType: {
    ACKNOWLEDGEMENT: "인사말 및 안내",
    REVIEW_NOTICE: "검토 예고",
    EVIDENCE_BASED_FINDING: "근거 기반 사실",
    PROPOSED_NEXT_STEP: "처리 방향 제안",
    FACT: "사실관계",
    LEGAL_BASIS: "법적 근거",
    OPINION: "의견",
    CONCLUSION: "결론",
    ACTION_PLAN: "처리 계획",
  },
  jobType: {
    REDACT: "비식별화",
    EXTRACT_ATTACHMENT: "첨부파일 검사",
    CLASSIFY_ISSUES: "민원 분석",
    RETRIEVE: "근거자료 검색",
    DRAFT: "답변 초안 생성",
    VERIFY: "초안 검증",
    ANALYZE_COMPLAINT: "민원 분석",
    GENERATE_DRAFT: "답변 초안 생성",
  },
  runStatus: {
    PENDING: "대기 중",
    RUNNING: "실행 중",
    SUCCEEDED: "성공",
    FAILED: "실패",
    BLOCKED: "보류됨",
  },
  action: {
    REVIEW_PASS: "검토 통과",
    REVIEW_APPROVED: "검토 통과",
    REVIEW_REJECT: "검토 반려",
    REVIEW_REJECTED: "검토 반려",
    APPROVE: "최종 승인",
    APPROVED: "승인",
    APPROVAL_REJECT: "승인 반려",
    APPROVAL_REJECTED: "승인 반려",
    CREATE: "민원 접수",
    COMPLETE: "수동 완료",
  },
  actorRole: {
    REVIEWER: "검토자",
    APPROVER: "승인자",
    WORKER: "담당자",
    SYSTEM: "시스템",
  },
};

const WORKFLOW_STEPS = [
  {
    key: "received",
    title: "접수",
    description: "민원 원문을 저장하고 비식별 처리합니다.",
  },
  {
    key: "analysis",
    title: "분석",
    description: "유형, 긴급도, 위치, 복합 이슈를 분리합니다.",
  },
  {
    key: "department",
    title: "부서 선택",
    description: "추천 부서 중 담당 부서를 선택합니다.",
  },
  {
    key: "evidence",
    title: "근거 확인",
    description: "답변에 쓸 수 있는 공식 자료인지 확인합니다.",
  },
  {
    key: "draft",
    title: "초안·승인",
    description: "확인된 근거로 공문 초안을 검토하고 승인합니다.",
  },
];

const el = Object.fromEntries(
  [...document.querySelectorAll("[id]")].map((node) => [node.id, node]),
);

function idempotencyKey(action) {
  return `${action}-${crypto.randomUUID()}`;
}

function csrfToken() {
  const item = document.cookie.split("; ").find((value) => value.startsWith("XSRF-TOKEN="));
  return item ? decodeURIComponent(item.split("=", 2)[1]) : null;
}

async function api(path, options = {}) {
  const method = options.method || "GET";
  const headers = { Accept: "application/json", ...(options.headers || {}) };
  if (options.body) headers["Content-Type"] = "application/json";
  const token = csrfToken();
  if (method !== "GET" && token) headers["X-XSRF-TOKEN"] = token;

  const response = await fetch(path, { ...options, method, headers, credentials: "same-origin" });
  const body = await response.json().catch(() => null);
  if (!response.ok || !body?.success) {
    throw new Error(body?.error?.message || body?.message || `${response.status} ${response.statusText}`);
  }
  return body.data;
}

function mutationHeaders(action, version) {
  const headers = { "Idempotency-Key": idempotencyKey(action) };
  if (version !== undefined && version !== null) headers["If-Match"] = `"${version}"`;
  return headers;
}

function showMessage(text, error = false) {
  el.message.textContent = text;
  el.message.classList.toggle("is-error", error);
  el.message.classList.remove("is-hidden");
  window.setTimeout(() => el.message.classList.add("is-hidden"), 6000);
}

async function checkServer() {
  try {
    const response = await fetch("/dashboard/index.html", { method: "HEAD", credentials: "same-origin" });
    if (!response.ok) throw new Error(`Server check failed: ${response.status}`);
    el.serverDot.className = "status-dot ok";
    el.serverStatus.textContent = "서버 연결됨";
  } catch {
    el.serverDot.className = "status-dot error";
    el.serverStatus.textContent = "서버 연결 실패";
  }
}

async function createComplaint() {
  if (!el.rawText.value.trim()) return showMessage("민원 원문을 입력하세요.", true);
  try {
    const complaint = await api("/api/v1/complaints", {
      method: "POST",
      headers: mutationHeaders("create"),
      body: JSON.stringify({
        sourceChannel: el.sourceChannel.value,
        rawText: el.rawText.value.trim(),
        locationText: el.locationText.value.trim() || null,
      }),
    });
    el.complaintId.value = complaint.id;
    await loadComplaint(complaint.id);
    showMessage("민원이 접수되었습니다. 분석은 아직 실행되지 않았습니다.");
  } catch (error) {
    showMessage(error.message, true);
  }
}

async function loadComplaint(id = el.complaintId.value.trim()) {
  if (!id) return showMessage("민원 UUID를 입력하세요.", true);
  try {
    state.detail = await api(`/api/v1/complaints/${id}`);
    el.complaintId.value = id;
    render();
  } catch (error) {
    showMessage(error.message, true);
  }
}

async function enqueue(kind) {
  const complaint = state.detail?.complaint;
  if (!complaint) return;
  if (kind === "draft" && !allIssuesHaveVerifiedDepartment()) {
    showMessage("초안 생성 전 모든 이슈에서 담당 부서를 선택하고 확인해야 합니다.", true);
    return;
  }
  try {
    state.run = await api(`/api/v1/complaints/${complaint.id}/${kind}-runs`, {
      method: "POST",
      headers: mutationHeaders(kind, complaint.version),
    });
    renderRun();
    await pollRun(state.run.id);
  } catch (error) {
    showMessage(error.message, true);
  } finally {
    document.getElementById("loadingOverlay")?.classList.add("is-hidden");
    await loadComplaint(complaint.id);
  }
}

async function pollRun(id) {
  for (let attempt = 0; attempt < 150; attempt += 1) {
    state.run = await api(`/api/v1/runs/${id}`);
    renderRun();
    const retryExhausted = state.run.status === "FAILED" && state.run.attempts >= state.run.maxAttempts;
    if (["SUCCEEDED", "BLOCKED"].includes(state.run.status) || retryExhausted) return;
    await new Promise((resolve) => window.setTimeout(resolve, 700));
  }
  showMessage("작업 대기 시간이 초과되었습니다. 새로고침으로 상태를 확인하세요.", true);
}

async function confirmLocation(issueId) {
  const locationText = window.prompt("사람이 확인한 위치를 입력하세요.");
  if (!locationText?.trim()) return;
  try {
    await api(`/api/v1/issues/${issueId}/location-confirmations`, {
      method: "POST",
      headers: mutationHeaders("location", state.detail.complaint.version),
      body: JSON.stringify({ locationText: locationText.trim() }),
    });
    await loadComplaint(state.detail.complaint.id);
    showMessage("위치가 사람 확인으로 기록되었습니다.");
  } catch (error) {
    showMessage(error.message, true);
  }
}

async function confirmDepartment(issueId, departmentCode) {
  if (!state.detail?.complaint) return;
  try {
    state.detail = await api(`/api/v1/issues/${issueId}/department-confirmations`, {
      method: "POST",
      headers: mutationHeaders("department", state.detail.complaint.version),
      body: JSON.stringify({ departmentCode }),
    });
    render();
    const issue = state.detail.issues.find((item) => item.id === issueId);
    const selected = issue?.departmentCandidateDetails?.find((item) => item.code === departmentCode);
    if (selected?.verified && !state.detail.complaint.workflowBlocker) {
      showMessage(`${departmentLabel(departmentCode)} 담당 부서 선택이 확인되었습니다. 초안 생성을 진행할 수 있습니다.`);
    } else if (selected?.verified) {
      showMessage(`${departmentLabel(departmentCode)} 담당 부서 선택은 확인되었지만, ${label("blocker", state.detail.complaint.workflowBlocker)} 상태가 남아 있습니다.`);
    } else {
      showMessage("선택한 부서를 사용할 수 없습니다. 확인 결과를 보고 다른 후보를 선택하세요.", true);
    }
  } catch (error) {
    showMessage(error.message, true);
  }
}

async function decide(path, approved, action) {
  const draft = state.detail?.draft;
  if (!draft) return;
  try {
    await api(`/api/v1/drafts/${draft.id}/${path}`, {
      method: "POST",
      headers: mutationHeaders(action, draft.version),
      body: JSON.stringify({ approved, notes: el.reviewNotes.value.trim() || null }),
    });
    await loadComplaint(state.detail.complaint.id);
    showMessage(approved ? "결정이 기록되었습니다." : "거절 또는 반려가 기록되었습니다.");
  } catch (error) {
    showMessage(error.message, true);
  }
}

async function completeComplaint() {
  const complaint = state.detail?.complaint;
  if (!complaint) return;
  try {
    await api(`/api/v1/complaints/${complaint.id}/complete`, {
      method: "POST",
      headers: mutationHeaders("complete", complaint.version),
    });
    await loadComplaint(complaint.id);
    showMessage("외부 발송 없이 수동 완료 기록만 저장되었습니다.");
  } catch (error) {
    showMessage(error.message, true);
  }
}

function resetSession() {
  state.detail = null;
  state.run = null;
  el.complaintId.value = "";
  el.rawText.value = "";
  el.locationText.value = "";
  el.sourceChannel.value = "WEB";
  el.workspace.classList.add("is-hidden");
  el.runStatus.classList.add("empty");
  el.runStatus.innerHTML = "";
  render();
  showMessage("세션이 종료되었습니다. 새로운 민원을 접수할 수 있습니다.");
}

function render() {
  if (!state.detail) {
    el.workspace.classList.add("is-hidden");
    return;
  }
  const {
    complaint,
    analysis,
    issues = [],
    draft,
    draftClaims = [],
    evidence = [],
    verificationResults = [],
    aiRuns = [],
    humanReviews = [],
  } = state.detail;
  el.workspace.classList.remove("is-hidden");
  el.receiptNumber.textContent = complaint.receiptNumber;
  el.status.textContent = label("status", complaint.status);
  el.blocker.textContent = complaint.workflowBlocker ? label("blocker", complaint.workflowBlocker) : "없음";
  el.version.textContent = complaint.version;
  el.location.textContent = complaint.locationText || "미확정";
  el.redactedText.textContent = complaint.redactedText;
  el.analysis.classList.toggle("empty", !analysis);
  el.analysis.innerHTML = analysis
    ? `
      <strong>${label("complaintType", analysis.complaintType)} · ${label("urgency", analysis.urgency)}</strong>
      <p>감정 정보: ${label("sentiment", analysis.sentiment)} · 초기 담당 후보: ${departmentLabel(analysis.departmentCode)}</p>
      <p>아래 추천 부서 중 실제 담당 부서를 선택해야 초안을 만들 수 있습니다.</p>
    `
    : "분석 결과가 없습니다.";

  el.issues.innerHTML = issues.map((issue) => `
    <article class="issue">
      <div><strong>이슈 ${issue.issueIndex + 1}: ${escapeHtml(issue.summary)}</strong><span>${label("status", issue.status)}</span></div>
      <p>유형 ${label("complaintType", issue.complaintType)} · 관할 ${explainValue(issue.jurisdictionStatus)} · 안전 ${explainValue(issue.safetyRisk)} · 처리 가능성 ${explainValue(issue.processability)}</p>
      <p>위치 후보 ${escapeHtml((issue.locationCandidates || []).join(", ")) || "없음"}</p>
      ${renderDepartmentCandidates(issue)}
      <button class="secondary" data-location-id="${issue.id}" type="button">위치 사람 확인</button>
    </article>
  `).join("") || '<div class="empty">분석 후 복합 이슈가 표시됩니다.</div>';

  el.evidence.classList.toggle("empty", evidence.length === 0);
  el.evidence.innerHTML = evidence.map((item) => `
    <article>
      <strong>${escapeHtml(translateTitle(item.title))}</strong>
      <dl>
        <dt>주장 관계</dt><dd>${item.supportsClaim ? "지지 가능" : "반대 또는 검토 필요"}</dd>
        <dt>자료 상태</dt><dd>${sourceStatusLabel(item.sourceStatus)} ${infoIcon(explainSourceStatus(item.sourceStatus))}</dd>
        <dt>자료 종류</dt><dd>${sourceTypeLabel(item.sourceType)} ${infoIcon(explainSourceType(item.sourceType))}</dd>
        <dt>법적 근거</dt><dd>${escapeHtml(translateLegalBasis(item.legalBasis) || "미지정")}</dd>
        <dt>출처 버전</dt><dd>${escapeHtml(translateSourceVersion(item.sourceVersion))}</dd>
        <dt>관할</dt><dd>${escapeHtml(item.jurisdictionCode || "미지정")}</dd>
        <dt>시행</dt><dd>${escapeHtml(item.effectiveFrom || "미지정")} ~ ${escapeHtml(item.effectiveTo || "현재")}</dd>
        <dt>자료 번호</dt><dd>${escapeHtml(shortHash(item.contentHash))} ${infoIcon("자료가 처리 중 바뀌지 않았는지 확인하기 위한 내부 식별 번호입니다.")}</dd>
      </dl>
      <p>${escapeHtml(truncateContent(item.content))}</p>
      ${safeHttpUrl(item.sourceUrl) ? `<a href="${escapeHtml(item.sourceUrl)}" target="_blank" rel="noreferrer">원 출처</a>` : ""}
    </article>
  `).join("") || "연결된 근거가 없습니다.";

  el.draftStatus.textContent = draft ? `${label("status", draft.status)} · v${draft.version}` : "초안 없음";
  el.draftText.textContent = draft?.draftText || "확인된 공식 근거가 있어야 초안이 생성됩니다.";
  el.draftClaims.classList.toggle("empty", draftClaims.length === 0);
  el.draftClaims.innerHTML = draftClaims.map((claim) => `
    <article class="audit-item">
      <strong>주장 ${claim.claimIndex + 1} · ${escapeHtml(label("claimType", claim.claimType))}</strong>
      <p>${escapeHtml(claim.claimText)}</p>
      <p>근거 ID ${escapeHtml((claim.evidenceSourceIds || []).join(", "))}</p>
    </article>
  `).join("") || "구조화 주장이 없습니다.";
  el.verificationResults.classList.toggle("empty", verificationResults.length === 0);
  el.verificationResults.innerHTML = verificationResults.map((item) => `
    <article class="${item.hardFailure ? "audit-item hard-failure" : "audit-item"}">
      <strong>${verificationRuleLabel(item.ruleCode)} · ${verificationStatusLabel(item.status)}</strong>
      ${infoIcon(`${verificationRuleHelp(item.ruleCode)} ${cleanSystemMessage(item.message)}`)}
      <p>${cleanSystemMessage(item.message)}</p>
    </article>
  `).join("") || "확인 결과가 없습니다.";
  el.aiRuns.classList.toggle("empty", aiRuns.length === 0);
  el.aiRuns.innerHTML = aiRuns.map((item) => `
    <article class="audit-item">
      <strong>${escapeHtml(label("jobType", item.taskType))} · ${escapeHtml(label("runStatus", item.status))}</strong>
      <p>${escapeHtml(item.provider)} / ${escapeHtml(item.modelName)}</p>
      <p>적용한 기준 ${escapeHtml(item.promptVersion)} · 결과 양식 ${escapeHtml(item.schemaVersion)}</p>
      <p>입력 번호 ${escapeHtml(shortHash(item.inputHash))} · 결과 번호 ${escapeHtml(shortHash(item.outputHash || "없음"))} ${infoIcon("입력과 결과가 처리 중 바뀌지 않았는지 확인하기 위한 내부 식별 번호입니다.")}</p>
      <p>사용량 ${item.costUnits} · 처리 시간 ${item.durationMs}ms · 재시도 ${item.retryCount}</p>
      ${item.failureReason ? `<p class="failure">${escapeHtml(cleanSystemMessage(item.failureReason))}</p>` : ""}
    </article>
  `).join("") || "AI 실행 기록이 없습니다.";
  el.humanReviews.classList.toggle("empty", humanReviews.length === 0);
  el.humanReviews.innerHTML = humanReviews.map((item) => {
    const actorDisplay = ({
      "system-approver": "시스템 자동 승인",
      "system-reviewer": "시스템 자동 검토",
      "system": "시스템",
    })[item.actor] || item.actor;
    return `
    <article class="audit-item">
      <strong>${escapeHtml(label("action", item.action))} · ${escapeHtml(label("actorRole", item.actorRole))}</strong>
      <p>${escapeHtml(actorDisplay)}${item.notes ? ` · ${escapeHtml(item.notes)}` : ""}</p>
    </article>
  `;
  }).join("") || "검토·승인 이력이 없습니다.";
  document.querySelectorAll("[data-location-id]").forEach((button) => {
    button.addEventListener("click", () => confirmLocation(button.dataset.locationId));
  });
  document.querySelectorAll("[data-department-issue-id]").forEach((button) => {
    button.addEventListener("click", () => confirmDepartment(button.dataset.departmentIssueId, button.dataset.departmentCode));
  });
  updateWorkflowActions();
  renderWorkflow();
}

function renderRun() {
  if (!state.run) return;
  el.runStatus.classList.remove("empty");
  el.runStatus.innerHTML = `
    <strong>${label("jobType", state.run.jobType)} · ${label("runStatus", state.run.status)}</strong>
    <p>시도 ${state.run.attempts}/${state.run.maxAttempts}</p>
    ${state.run.failureReason ? `<p class="failure">${escapeHtml(cleanSystemMessage(state.run.failureReason))}</p>` : ""}
  `;

  const overlay = document.getElementById("loadingOverlay");
  if (overlay) {
    if (["PENDING", "RUNNING"].includes(state.run.status)) {
      overlay.classList.remove("is-hidden");
      document.getElementById("overlayTitle").textContent = `${label("jobType", state.run.jobType)} 진행 중`;
      document.getElementById("overlayStatus").textContent = "서버에서 AI 분석 및 정합성 검증 규칙을 수행하고 있습니다. 잠시만 기다려주세요...";
      document.getElementById("overlayAttempts").textContent = `시도 ${state.run.attempts}/${state.run.maxAttempts}`;
    } else {
      overlay.classList.add("is-hidden");
    }
  }
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function label(group, value) {
  if (!value) return "-";
  return LABELS[group]?.[value] || value;
}

function departmentLabel(code) {
  if (!code) return "-";
  return LABELS.department[code] || code;
}

function explainValue(value) {
  if (!value) return "-";
  const map = {
    NEEDS_LOCATION: "위치 확인 필요",
    NEEDS_JURISDICTION: "관할 확인 필요",
    NEEDS_REVIEW: "검토 필요",
    PILOT_CANDIDATE: "시범 운영 대상",
    PROCESSABLE: "처리 가능",
    EMERGENCY: "긴급",
    HIGH: "높음",
    NORMAL: "보통",
    LOW: "낮음",
    NEUTRAL: "중립",
    DISCOMFORT: "불편",
    ANGER: "분노",
    ANXIETY: "불안",
  };
  return map[value] ?? String(value).replaceAll("_", " ");
}

function translateTitle(title) {
  const titles = {
    "Legacy waste handling summary": "생활폐기물 무단투기 단속 지침",
    "Legacy road damage response summary": "도로 시설물 파손 및 포트홀 대응 매뉴얼",
    "Legacy illegal parking response summary": "불법 주정차 단속 및 처리 업무 매뉴얼",
    "General civil complaint handling manual": "일반 민원 처리 매뉴얼",
    "Legacy hazardous material response summary": "경보 및 대테러 생화학 위험물 처리 조례",
    "Conflicting official waste provision": "생활폐기물 처리 상충 조례",
    "Unrelated national road provision": "도로 포장 지침",
    "Road facility handling reference": "도로 시설물 처리 참고 자료",
    "Waste handling reference": "생활폐기물 처리 참고 자료",
    "Illegal parking reference": "불법 주정차 처리 참고 자료",
    "Demo Road Management": "도로관리과 (시연)",
  };
  return titles[title] || title;
}

function translateLegalBasis(value) {
  if (!value) return null;
  const map = {
    "Road facility handling reference": "도로 시설물 처리 참고 자료",
    "Waste handling reference": "생활폐기물 처리 참고 자료",
    "Illegal parking reference": "불법 주정차 처리 참고 자료",
    "Hazardous material reference": "위험물 처리 참고 자료",
    "Legacy road damage response summary": "도로 시설물 파손 및 포트홀 대응 매뉴얼",
    "Legacy waste handling summary": "생활폐기물 무단투기 단속 지침",
    "Legacy illegal parking response summary": "불법 주정차 단속 및 처리 업무 매뉴얼",
    "Legacy hazardous material response summary": "경보 및 대테러 생화학 위험물 처리 조례",
    "General civil complaint handling manual": "일반 민원 처리 매뉴얼",
  };
  return map[value] || value;
}

function translateContent(value) {
  return value || "";
}

function truncateContent(content, limit = 300) {
  if (!content) return "";
  if (content.length <= limit) return content;
  return content.slice(0, limit) + "... (이하 생략 - 전문은 하단 원 출처 참고)";
}

function translateSourceVersion(value) {
  if (!value) return "미지정";
  return value
    .replace("SYNTHETIC_TEST_V1", "시연 테스트 v1")
    .replace("SYNTHETIC_DEMO_V1", "시연 데모 v1")
    .replace("SYNTHETIC_", "시연 ")
    .replace("_V", " v");
}

function infoIcon(text) {
  if (!text) return "";
  return `<span class="info-tip" tabindex="0" aria-label="${escapeHtml(text)}"><svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="info-svg"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg><span role="tooltip">${escapeHtml(text)}</span></span>`;
}

function cleanDepartmentReason(reason, score, verified) {
  const value = String(reason || "");
  if (value.startsWith("RULE_BASED:")) {
    const parts = value.split(";");
    const ruleText = parts[0].replace("RULE_BASED:", "").trim();
    const typePart = parts.find(p => p.trim().startsWith("type="))?.split("=")[1] || "";
    const deptPart = parts.find(p => p.trim().startsWith("department="))?.split("=")[1] || "";
    const typeLabel = LABELS.complaintType[typePart] || typePart;
    const deptLabel = LABELS.department[deptPart] || deptPart;
    return `관할 배정 규칙 데이터베이스(assignment_rules 테이블)의 규칙 문서(원문: "${ruleText}")에 근거하여, 민원 유형(${typeLabel})에 매핑된 1순위 관할 부서인 ${deptLabel}(으)로 배정되었습니다.`;
  }
  if (value.startsWith("FALLBACK:")) {
    const parts = value.split(";");
    const typePart = parts.find(p => p.trim().startsWith("type="))?.split("=")[1] || "";
    const deptPart = parts.find(p => p.trim().startsWith("department="))?.split("=")[1] || "";
    const typeLabel = LABELS.complaintType[typePart] || typePart;
    const deptLabel = LABELS.department[deptPart] || deptPart;
    return `데이터베이스에 배정 규칙이 존재하지 않아, 시스템 기본 분류 맵(defaultDepartmentForType 설정)을 근거로 민원 유형(${typeLabel})의 기본 매핑 부서인 ${deptLabel}(으)로 배정되었습니다.`;
  }
  if (value.startsWith("AI_MODEL:")) {
    const parts = value.split(";");
    const model = parts[0].replace("AI_MODEL:", "").trim();
    const scorePart = parts.find(p => p.trim().startsWith("score="))?.split("=")[1] || "";
    const deptPart = parts.find(p => p.trim().startsWith("department="))?.split("=")[1] || "";
    const deptLabel = LABELS.department[deptPart] || deptPart;
    return `AI 분석 모델(${model})이 민원 내용을 기계 학습된 업무 분류 기준과 대조하여 추천 점수 ${scorePart}점을 산출함에 따라, ${deptLabel}을(를) 추가 후보 부서(Top 3)로 제안하였습니다.`;
  }
  if (value.includes("SYNTHETIC_DEMO assignment rule")) {
    return "민원 유형과 시범 운영 배정 기준이 이 부서와 가장 잘 맞아 1순위로 추천했습니다.";
  }
  if (value.includes("AI Top-3 candidate")) {
    return "민원 내용과 기존 업무 분류 기준을 비교해 담당 가능성이 높은 부서로 추천했습니다.";
  }
  if (value.includes("이전 응답 형식")) {
    return "기존 분석 결과에 포함된 담당 부서 후보입니다.";
  }
  if (verified) {
    return "담당자가 선택했고 시스템 확인도 완료되었습니다.";
  }
  if (value.trim()) {
    return value
      .replace("staff confirmation required", "담당자 확인 필요")
      .replace("human review required", "담당자 확인 필요")
      .replace(/score=[0-9]+/g, "")
      .replace(/;/g, ",")
      .trim();
  }
  return score === null || score === undefined
    ? "추천 근거가 별도로 제공되지 않았습니다."
    : "민원 내용과 업무 기준을 비교해 계산한 추천 결과입니다.";
}

function departmentScoreHelp(candidate) {
  const score = candidate.score === null || candidate.score === undefined ? "점수 없음" : `${candidate.score}점`;
  return `${score}은 민원 내용, 위치 단서, 부서 업무 범위, 기존 배정 기준을 비교해 산출한 참고 점수입니다. 담당자가 선택하면 실제 담당 부서로 지정해도 되는지 한 번 더 확인합니다.`;
}

function sourceStatusLabel(value) {
  return ({
    VERIFIED_OFFICIAL: "공식 확인됨",
    VERIFIED_INTERNAL: "내부 검증 완료",
    SYNTHETIC_DEMO: "시연용 자료",
    UNVERIFIED_LEGACY: "확인 필요",
    STALE: "최신 여부 확인 필요",
  })[value] || escapeHtml(value || "미지정");
}

function explainSourceStatus(value) {
  return ({
    VERIFIED_OFFICIAL: "공식 출처에서 확인된 자료입니다.",
    VERIFIED_INTERNAL: "내부 검토를 거쳐 확인된 자료입니다. 공식 법령 근거로는 사용할 수 없습니다.",
    SYNTHETIC_DEMO: "시연 환경에서 동작을 보여주기 위해 넣은 자료입니다.",
    UNVERIFIED_LEGACY: "공식 출처 확인이 끝나지 않아 답변 근거로 쓰면 안 됩니다.",
    STALE: "최신 자료인지 다시 확인해야 합니다.",
  })[value] || "자료 상태를 나타냅니다.";
}

function sourceTypeLabel(value) {
  return ({
    OFFICIAL_LAW: "법령",
    LOCAL_ORDINANCE: "자치법규",
    PROCEDURE: "처리 절차",
    STYLE_REFERENCE: "문장 참고",
    HISTORICAL_CASE: "이전 사례",
  })[value] || escapeHtml(value || "미지정");
}

function explainSourceType(value) {
  return ({
    OFFICIAL_LAW: "법령 근거로 사용할 수 있는 자료입니다.",
    LOCAL_ORDINANCE: "지자체 조례나 규칙 같은 지역 기준 자료입니다.",
    PROCEDURE: "민원 처리 절차를 설명하는 참고 자료입니다.",
    STYLE_REFERENCE: "답변 문체 참고용이며 법적 근거는 아닙니다.",
    HISTORICAL_CASE: "이전 처리 사례 참고용이며 법적 근거는 아닙니다.",
  })[value] || "자료가 어떤 목적으로 쓰이는지 나타냅니다.";
}

function verificationRuleLabel(value) {
  return ({
    OFFICIAL_EVIDENCE_REQUIRED: "공식 근거 확인",
    EVIDENCE_SOURCE_ALLOWED: "사용 가능한 자료 확인",
    JURISDICTION_FILTER: "관할 확인",
    DEPARTMENT_SELECTION: "담당 부서 확인",
    DRAFT_CLAIM_EVIDENCE: "초안 근거 연결 확인",
    CLAIM_EVIDENCE_COVERAGE: "초안-근거 연결 확인",
    SOURCE_EFFECTIVE_STATUS: "근거 유효기간 확인",
    REQUIRED_SOURCE_METADATA: "출처 메타데이터 확인",
    CONFLICT_SCAN: "근거 충돌 검사",
    PII_OUTPUT_CHECK: "개인정보 포함 여부 확인",
    PROCESSING_JOB_FAILURE: "처리 작업 오류",
  })[value] || cleanCode(value);
}

function verificationRuleHelp(value) {
  return ({
    OFFICIAL_EVIDENCE_REQUIRED: "답변에 공식 근거가 포함되어 있는지 확인합니다.",
    EVIDENCE_SOURCE_ALLOWED: "답변 근거로 사용해도 되는 자료인지 확인합니다.",
    JURISDICTION_FILTER: "우리 기관에서 처리할 수 있는 민원인지 확인합니다.",
    DEPARTMENT_SELECTION: "선택한 부서가 추천 후보와 업무 기준에 맞는지 확인합니다.",
    DRAFT_CLAIM_EVIDENCE: "초안의 각 주장에 근거 자료가 연결되어 있는지 확인합니다.",
    CLAIM_EVIDENCE_COVERAGE: "생성된 모든 주장이 변경 불가능한 근거 자료와 연결되어 있는지 확인합니다.",
    SOURCE_EFFECTIVE_STATUS: "근거 자료가 처리일 기준으로 유효한지 확인합니다.",
    REQUIRED_SOURCE_METADATA: "공식 근거에 출처 URL, 법적 근거, 버전, 내용 해시가 모두 있는지 확인합니다.",
    CONFLICT_SCAN: "동일한 법적 근거에 대해 서로 다른 공식 자료가 충돌하는지 검사합니다.",
    PII_OUTPUT_CHECK: "초안에 개인정보 패턴이 포함되어 있지 않은지 확인합니다.",
    PROCESSING_JOB_FAILURE: "작업 처리 중 오류가 발생한 경우 표시됩니다.",
  })[value] || "답변 전 확인해야 하는 항목입니다.";
}

function verificationStatusLabel(value) {
  return ({
    PASSED: "통과",
    FAILED: "확인 필요",
    BLOCKED: "진행 보류",
    WARNING: "주의",
  })[value] || cleanCode(value);
}

function cleanSystemMessage(value) {
  return String(value || "")
    .replace("Human-selected department verified", "담당자가 선택한 부서가 확인되었습니다.")
    .replace("Selected department is not active", "현재 사용할 수 없는 부서입니다.")
    .replace(/Selected department conflicts with deterministic assignment rule: expected ([A-Z_]+)/, (_, code) => `업무 배정 기준과 다릅니다. 권장 부서: ${departmentLabel(code)}`)
    .replace("At least one verified official legal source is required", "공식 근거 자료가 최소 1개 필요합니다.")
    .replace("Official legal source is optional for routine civil complaints", "일반 민원은 공식 법령 근거 없이 처리할 수 있습니다.")
    .replace("Every source must be verified and effective on the processing date", "모든 근거 자료는 공식 확인이 끝났고 처리일 기준으로 적용 가능해야 합니다.")
    .replace("Legal claims in the synthetic pilot require official national-law evidence", "법령 관련 주장에는 국가 공식 법령 근거가 필요합니다.")
    .replace("Official legal sources require a source URL, legal basis, source version, and content hash", "공식 법령 근거는 출처 URL, 법적 근거, 버전, 내용 해시가 모두 있어야 합니다.")
    .replace("Overlapping official sources for the same legal basis must contain consistent content", "동일한 법적 근거에 대해 서로 다른 공식 자료가 충돌합니다.")
    .replace("Draft output must not contain recognizable PII patterns", "초안에 개인정보 패턴이 포함되어 있어 발송할 수 없습니다.")
    .replace("All generated claims are linked to immutable evidence snapshots", "생성된 모든 주장이 변경 불가능한 근거 자료와 연결되어 있습니다.")
    .replace("Deterministic verification hard gate failed:", "필수 확인 항목이 통과되지 않았습니다:")
    .replace("Unknown processing failure", "알 수 없는 처리 오류")
    .replace("Analysis schema validation failed:", "분석 결과 형식 오류:")
    .replace("Staff review required.", "담당자 검토가 필요합니다.")
    .replace("No action, acceptance, dispatch, or completion has been performed automatically.", "자동으로 수락·발송·완료 처리된 사항이 없습니다.");
}

function cleanCode(value) {
  if (!value) return "-";
  return String(value).replaceAll("_", " ").toLowerCase().replace(/(^|\s)\S/g, (letter) => letter.toUpperCase());
}

function shortHash(value) {
  const text = String(value || "없음");
  return text.length > 14 ? `${text.slice(0, 14)}...` : text;
}

function allIssuesHaveVerifiedDepartment() {
  const issues = state.detail?.issues || [];
  return issues.length > 0 && issues.every((issue) =>
    (issue.departmentCandidateDetails || []).some((candidate) => candidate.verified),
  );
}

function renderDepartmentCandidates(issue) {
  const details = issue.departmentCandidateDetails || [];
  const candidates = details.length
    ? details
    : (issue.departmentCandidates || []).map((code) => ({
        code,
        status: "CANDIDATE",
        recommendationReason: "이전 응답 형식의 담당 후보입니다.",
        score: null,
        selected: false,
        verified: false,
      }));
  if (!candidates.length) {
    return '<div class="candidate-empty">담당 부서 후보가 없습니다. 관할 검토가 필요합니다.</div>';
  }
  return `
    <div class="candidate-panel">
      <div class="candidate-heading">
        <strong>담당 부서 추천 ${candidates.length}개</strong>
        ${infoIcon("추천 점수는 민원 내용, 위치, 업무 배정 규칙, 유사 처리 이력을 함께 보고 계산한 참고 점수입니다. 최종 담당 부서는 담당자가 선택합니다.")}
      </div>
      <div class="candidate-list">
        ${candidates.map((candidate) => renderDepartmentCandidate(issue, candidate)).join("")}
      </div>
    </div>
  `;
}

function renderDepartmentCandidate(issue, candidate) {
  const disabled = candidate.verified ? "disabled" : "";
  const selected = candidate.selected ? " selected" : "";
  const verified = candidate.verified ? " verified" : "";
  const score = candidate.score === null || candidate.score === undefined ? "점수 없음" : `${candidate.score}점`;
  const numericScore = Number(candidate.score);
  const scoreValue = Number.isFinite(numericScore) ? Math.max(0, Math.min(100, numericScore)) : 0;
  return `
    <article class="candidate${selected}${verified}">
      <div>
        <strong>${escapeHtml(departmentLabel(candidate.code))}</strong>
        <span>${label("status", candidate.status)} · ${score}</span>
      </div>
      <div class="score-meter" aria-label="부서 추천 참고 점수">
        <span style="width: ${scoreValue}%"></span>
      </div>
      <p>${escapeHtml(cleanDepartmentReason(candidate.recommendationReason, candidate.score, candidate.verified))} ${infoIcon(departmentScoreHelp(candidate))}</p>
      <button class="secondary" data-department-issue-id="${issue.id}" data-department-code="${escapeHtml(candidate.code)}" type="button" ${disabled}>
        ${candidate.verified ? "확인 완료" : "이 부서 선택"}
      </button>
    </article>
  `;
}

function updateWorkflowActions() {
  const hasComplaint = Boolean(state.detail?.complaint);
  const hasAnalysis = Boolean(state.detail?.analysis);
  const departmentReady = allIssuesHaveVerifiedDepartment();
  const blocker = state.detail?.complaint?.workflowBlocker;
  const draftBlockReason = draftBlockerText(hasAnalysis, departmentReady, blocker);
  el.analysisButton.disabled = !hasComplaint;
  el.draftButton.disabled = !hasAnalysis || !departmentReady || Boolean(blocker);
  el.draftButton.title = draftBlockReason;
  el.actionHint.textContent = draftBlockReason
    ? `다음 작업: ${draftBlockReason}`
    : "다음 작업: 근거 기반 초안 생성이 가능합니다.";
}

function draftBlockerText(hasAnalysis, departmentReady, blocker) {
  if (!hasAnalysis) return "분석 작업을 먼저 실행하세요.";
  if (!departmentReady) return "모든 이슈에서 담당 부서를 선택하고 확인해야 합니다.";
  if (blocker) return `${label("blocker", blocker)} 상태를 먼저 해소해야 합니다.`;
  return "";
}

function renderWorkflow() {
  if (!state.detail?.complaint) return;
  const currentIndex = workflowIndex();
  el.workflowSteps.innerHTML = WORKFLOW_STEPS.map((step, index) => {
    const stateClass = index < currentIndex ? "done" : index === currentIndex ? "active" : "waiting";
    return `
      <article class="workflow-step ${stateClass}">
        <span>${String(index + 1).padStart(2, "0")}</span>
        <strong>${step.title}</strong>
        <p>${step.description}</p>
      </article>
    `;
  }).join("");
  el.nextAction.textContent = nextActionText();
}

function workflowIndex() {
  const detail = state.detail;
  if (!detail?.complaint) return 0;
  if (detail.draft) return 4;
  if ((detail.evidence || []).length || (detail.verificationResults || []).length) return 3;
  if (detail.analysis && !allIssuesHaveVerifiedDepartment()) return 2;
  if (detail.analysis) return 3;
  return 1;
}

function nextActionText() {
  const detail = state.detail;
  if (!detail?.analysis) return "민원을 접수했습니다. 분석 작업 요청을 눌러 이슈와 담당 부서 후보를 생성하세요.";
  if (!allIssuesHaveVerifiedDepartment()) return "이슈별 담당 부서 후보를 확인하고 한 개 부서를 선택하세요. 선택 결과는 서버에서 한 번 더 확인합니다.";
  if (detail.complaint.workflowBlocker) return `${label("blocker", detail.complaint.workflowBlocker)} 상태입니다. 위치 확인 또는 관할 검토를 먼저 완료하세요.`;
  if (!detail.draft) return "담당 부서 확인이 완료되었습니다. 근거 기반 초안 요청을 진행할 수 있습니다.";
  if (!detail.humanReviews?.length) return "초안이 생성되었습니다. 검토 통과 또는 반려 결정을 기록하세요.";
  return "검토·승인 이력을 확인하고 필요한 경우 수동 완료를 기록하세요.";
}

function safeHttpUrl(value) {
  try {
    const url = new URL(value);
    return ["http:", "https:"].includes(url.protocol);
  } catch {
    return false;
  }
}

el.createButton.addEventListener("click", createComplaint);
el.loadButton.addEventListener("click", () => loadComplaint());
el.refreshButton.addEventListener("click", () => loadComplaint());
el.analysisButton.addEventListener("click", () => enqueue("analysis"));
el.draftButton.addEventListener("click", () => enqueue("draft"));
el.reviewApproveButton.addEventListener("click", () => decide("reviews", true, "review-pass"));
el.reviewRejectButton.addEventListener("click", () => decide("reviews", false, "review-reject"));
el.approveButton.addEventListener("click", () => decide("approvals", true, "approve"));
el.rejectButton.addEventListener("click", () => decide("approvals", false, "approval-reject"));
el.completeButton.addEventListener("click", completeComplaint);
el.resetButton.addEventListener("click", resetSession);

checkServer();
