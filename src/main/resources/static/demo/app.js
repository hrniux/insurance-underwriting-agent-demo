"use strict";

const SCENARIO_API = "/api/v1/demo/scenarios";
const EVALUATION_API = "/api/v1/underwriting/evaluations";

const state = {
  scenarios: [],
  selectedPolicyNo: null,
  detail: null,
  evaluation: null,
  detailController: null,
  evaluationController: null
};

const LABELS = {
  decisions: {
    APPROVE: "自动通过",
    MANUAL_REVIEW: "人工复核",
    REJECT: "拒保"
  },
  risks: {
    LOW: "低风险",
    MEDIUM: "中风险",
    HIGH: "高风险",
    CRITICAL: "极高风险"
  },
  severities: {
    INFO: "提示",
    WARNING: "警告",
    HIGH: "高风险",
    CRITICAL: "严重"
  },
  statuses: {
    SUCCESS: "成功",
    FAILED: "失败",
    SKIPPED: "跳过"
  },
  hazards: {
    LOW: "低",
    MEDIUM: "中",
    HIGH: "高",
    RED: "红色预警"
  },
  fireProtection: {
    GOOD: "良好",
    ADEQUATE: "基本满足",
    INADEQUATE: "不满足",
    CRITICAL_DEFECT: "存在重大缺陷"
  },
  occupancies: {
    OFFICE: "办公楼",
    WAREHOUSE: "仓库",
    FACTORY: "制造厂房"
  },
  products: {
    PROPERTY: "企业财产险"
  },
  documents: {
    CLAUSE: "保险条款",
    MANUAL: "核保手册",
    CASE: "历史案例",
    POLICY: "内部政策",
    RISK_GUIDE: "风险指引"
  },
  steps: {
    QUESTION_UNDERSTANDING: "理解核保问题",
    BUSINESS_DATA_COLLECTION: "采集五类业务事实",
    KNOWLEDGE_RETRIEVAL: "检索核保知识",
    RISK_ANALYSIS: "分析综合风险",
    RULE_VALIDATION: "执行确定性规则",
    RECOMMENDATION_GENERATION: "生成核保建议",
    RESULT_PERSISTENCE: "保存可审计结果"
  },
  tools: {
    GET_POLICY: "保单信息工具",
    GET_QUOTATION: "报价信息工具",
    GET_UNDERWRITING_HISTORY: "历史核保工具",
    GET_SURVEY_REPORT: "风险查勘工具",
    GET_DISASTER_RISK: "灾害风险工具",
    VALIDATE_RULES: "规则校验工具"
  }
};

const FACT_GROUPS = [
  ["保单信息", detail => [
    ["保单号", detail.policy?.policyNo],
    ["险种", label("products", detail.policy?.productCode)],
    ["被保险人", detail.policy?.insuredName],
    ["行业 / 用途", label("occupancies", detail.policy?.occupancy)],
    ["风险地址", detail.policy?.address],
    ["保险期间", `${text(detail.policy?.startDate)} 至 ${text(detail.policy?.endDate)}`]
  ]],
  ["报价信息", detail => [
    ["保险金额", detail.sumInsuredDisplay],
    ["费率", formatRate(detail.quotation?.rate)],
    ["保费", detail.premiumDisplay],
    ["免赔额", detail.deductibleDisplay]
  ]],
  ["历史记录", detail => [
    ["三年出险次数", `${text(detail.history?.claimCountThreeYears, "0")} 次`],
    ["三年已赔金额", detail.paidLossThreeYearsDisplay],
    ["历史结论", joinValues(detail.history?.priorDecisions, "无历史核保记录")]
  ]],
  ["风险查勘", detail => [
    ["消防状态", label("fireProtection", detail.survey?.fireProtectionStatus)],
    ["排水整改", detail.survey?.drainageRemediationCompleted ? "已完成" : "未完成"],
    ["未决问题", joinValues(detail.survey?.openIssues, "无")],
    ["查勘结论", detail.survey?.conclusion]
  ]],
  ["灾害风险", detail => [
    ["风险区域", detail.disaster?.riskZone],
    ["暴雨", label("hazards", detail.disaster?.rainstorm)],
    ["洪水", label("hazards", detail.disaster?.flood)],
    ["火灾", label("hazards", detail.disaster?.fire)],
    ["数据日期", detail.disaster?.dataDate]
  ]]
];

function find(selector) {
  return document.querySelector(selector);
}

function text(value, fallback = "暂无") {
  return value === null || value === undefined || value === "" ? fallback : String(value);
}

function label(group, value) {
  return LABELS[group]?.[value] || text(value);
}

function joinValues(values, fallback = "暂无") {
  return Array.isArray(values) && values.length ? values.join("；") : fallback;
}

function formatRate(value) {
  const rate = Number(value);
  return Number.isFinite(rate) ? `${(rate * 100).toFixed(2)}%` : "暂无";
}

function clampScore(value) {
  const score = Number(value);
  return Number.isFinite(score) ? Math.min(100, Math.max(0, score)) : 0;
}

function el(tagName, className, content) {
  const node = document.createElement(tagName);
  if (className) node.className = className;
  if (content !== undefined) node.textContent = text(content);
  return node;
}

function setChildren(target, children) {
  target.replaceChildren(...children.filter(Boolean));
}

async function requestJson(url, options = {}) {
  const { headers = {}, ...requestOptions } = options;
  const response = await fetch(url, {
    ...requestOptions,
    headers: { Accept: "application/json", ...headers }
  });

  let payload = null;
  try {
    payload = await response.json();
  } catch (error) {
    if (response.ok) {
      throw new Error("服务返回了无法识别的数据格式。");
    }
  }

  if (!response.ok) {
    const message = payload?.detail || payload?.title || `请求失败（HTTP ${response.status}）`;
    const trace = payload?.traceId ? `，追踪编号：${payload.traceId}` : "";
    throw new Error(`${message}${trace}`);
  }
  return payload;
}

function createScenarioButton(scenario) {
  const button = el("button", "scenario-card");
  button.type = "button";
  button.setAttribute("aria-pressed", String(scenario.policyNo === state.selectedPolicyNo));
  button.append(
    el("span", "scenario-card__code", scenario.policyNo),
    el("strong", "scenario-card__title", scenario.name),
    el(
      "span",
      "scenario-card__decision",
      `预期：${text(scenario.expectedResult?.decisionLabel, label("decisions", scenario.expectedResult?.decision))}`
    ),
    el("span", "scenario-card__summary", scenario.summary)
  );
  button.addEventListener("click", () => selectScenario(scenario.policyNo));
  return button;
}

function renderScenarioButtons() {
  const buttons = state.scenarios.map(createScenarioButton);
  setChildren(find("#scenario-list"), buttons);
  find("#scenario-status").textContent = `已载入 ${buttons.length} 组虚构场景。`;
}

function createFactGroup(title, entries) {
  const group = el("section", "fact-group");
  const list = el("dl", "fact-list");
  for (const [term, value] of entries) {
    list.append(el("dt", null, term), el("dd", null, value));
  }
  group.append(el("h4", null, title), list);
  return group;
}

function renderScenarioDetail(detail) {
  find("#scenario-title").textContent = text(detail.name);
  find("#scenario-summary").textContent = text(detail.summary);
  find("#question-text").textContent = `核保问题：${text(detail.question)}`;

  const expected = find("#expected-decision");
  expected.className = "decision-badge";
  expected.dataset.decision = text(detail.expectedResult?.decision, "UNKNOWN");
  expected.textContent = `预期结论：${text(
    detail.expectedResult?.decisionLabel,
    label("decisions", detail.expectedResult?.decision)
  )}`;

  const learningPoints = Array.isArray(detail.learningPoints) ? detail.learningPoints : [];
  setChildren(find("#learning-points"), learningPoints.map(point => el("li", null, point)));
  setChildren(
    find("#facts-grid"),
    FACT_GROUPS.map(([title, readEntries]) => createFactGroup(title, readEntries(detail)))
  );

  find("#scenario-detail").hidden = false;
  find("#detail-status").hidden = true;
  find("#run-evaluation").disabled = false;
}

function renderDecisionSummary(evaluation, expected) {
  const wrapper = el("section", "decision-overview");
  const heading = el("div", "decision-overview__heading");
  const badge = el("p", "decision-badge", label("decisions", evaluation.decision));
  badge.dataset.decision = text(evaluation.decision, "UNKNOWN");
  const score = clampScore(evaluation.riskScore);
  const risk = el("div", "risk-score");
  risk.append(
    el("span", "risk-score__number", score),
    el("span", "risk-score__unit", "/ 100 风险分")
  );
  heading.append(badge, risk);

  const matches = evaluation.decision === expected?.decision;
  const comparison = el(
    "p",
    matches ? "comparison comparison--match" : "comparison comparison--different",
    matches
      ? "实际结论与场景预期一致"
      : `实际结论与预期不同（预期：${text(expected?.decisionLabel, label("decisions", expected?.decision))}）`
  );

  const bar = el("div", "risk-progress");
  bar.setAttribute("role", "progressbar");
  bar.setAttribute("aria-label", "核保风险分数");
  bar.setAttribute("aria-valuemin", "0");
  bar.setAttribute("aria-valuemax", "100");
  bar.setAttribute("aria-valuenow", String(score));
  const fill = el("span", "risk-progress__fill");
  fill.style.width = `${score}%`;
  bar.append(fill);

  wrapper.append(
    heading,
    comparison,
    el("p", "risk-label", `风险等级：${label("risks", evaluation.riskLevel)}`),
    bar,
    el("p", "model-summary", evaluation.summary)
  );
  setChildren(find("#decision-summary"), [wrapper]);
}

function renderTextList(targetId, title, items = []) {
  const section = el("section", "result-section");
  const list = el("ul", "result-list");
  const values = Array.isArray(items) && items.length ? items : ["暂无"];
  list.append(...values.map(item => el("li", null, item)));
  section.append(el("h3", null, title), list);
  setChildren(find(`#${targetId}`), [section]);
}

function renderRuleHits(items) {
  const section = el("section", "result-section");
  const grid = el("div", "card-grid");
  const cards = items.length ? items.map(item => {
    const card = el("article", "result-card");
    const impact = Number(item.scoreImpact);
    card.append(
      el("p", "result-card__meta", `${text(item.code)} · ${label("severities", item.severity)}`),
      el("h4", null, label("decisions", item.decision)),
      el("p", null, item.reason),
      el(
        "p",
        "score-impact",
        `风险分影响：${Number.isFinite(impact) && impact >= 0 ? "+" : ""}${text(item.scoreImpact, "0")}`
      )
    );
    return card;
  }) : [el("p", "empty-state", "本场景没有规则命中，基础风险分决定最终结果。")];
  grid.append(...cards);
  section.append(el("h3", null, "规则命中"), grid);
  setChildren(find("#rule-hit-list"), [section]);
}

function renderEvidence(items) {
  const section = el("section", "result-section");
  const grid = el("div", "card-grid");
  const cards = items.length ? items.map(item => {
    const card = el("article", "result-card");
    const relevance = Math.round(clampScore(Number(item.score) * 100));
    card.append(
      el("p", "result-card__meta", `${label("documents", item.type)} · 相关度 ${relevance}%`),
      el("h4", null, item.title),
      el("p", null, item.excerpt)
    );
    return card;
  }) : [el("p", "empty-state", "本场景没有返回知识证据。")];
  grid.append(...cards);
  section.append(el("h3", null, "知识证据"), grid);
  setChildren(find("#evidence-list"), [section]);
}

function renderStepTraces(items) {
  const section = el("section", "result-section");
  const list = el("ol", "step-timeline");
  const entries = items.map((item, index) => {
    const entry = el("li", "step-item");
    const content = [
      el("span", "step-index", index + 1),
      el("strong", null, label("steps", item.step)),
      el("span", "trace-status", `${label("statuses", item.status)} · ${text(item.durationMs, "0")} ms`),
      item.errorCode ? el("span", "error-code", `错误码：${item.errorCode}`) : null
    ];
    entry.append(...content.filter(Boolean));
    return entry;
  });
  list.append(...(entries.length ? entries : [el("li", "empty-state", "暂无步骤轨迹。")]));
  section.append(el("h3", null, "七步 Agent 执行轨迹"), list);
  setChildren(find("#step-timeline"), [section]);
}

function renderToolTraces(items) {
  const section = el("section", "result-section");
  const grid = el("div", "trace-grid");
  const cards = items.length ? items.map(item => {
    const card = el("article", "trace-card");
    const content = [
      el("h4", null, label("tools", item.toolName)),
      el("p", "result-card__meta", `${label("statuses", item.status)} · ${text(item.durationMs, "0")} ms`),
      el("p", null, `输入：${text(item.inputSummary)}`),
      el("p", null, `输出：${text(item.outputSummary)}`),
      item.errorCode ? el("p", "error-code", `错误码：${item.errorCode}`) : null
    ];
    card.append(...content.filter(Boolean));
    return card;
  }) : [el("p", "empty-state", "暂无工具调用记录。")];
  grid.append(...cards);
  section.append(el("h3", null, "工具调用记录"), grid);
  setChildren(find("#tool-trace-list"), [section]);
}

function renderEvaluation(evaluation, expected) {
  renderDecisionSummary(evaluation, expected);
  renderTextList("reason-list", "核保原因", evaluation.reasons);
  renderTextList("action-list", "建议动作", evaluation.recommendedActions);
  renderRuleHits(Array.isArray(evaluation.ruleHits) ? evaluation.ruleHits : []);
  renderEvidence(Array.isArray(evaluation.evidence) ? evaluation.evidence : []);
  renderStepTraces(Array.isArray(evaluation.stepTraces) ? evaluation.stepTraces : []);
  renderToolTraces(Array.isArray(evaluation.toolTraces) ? evaluation.toolTraces : []);
  find("#result-content").hidden = false;
  find("#evaluation-status").textContent = `核保完成，评估编号：${text(evaluation.id)}`;
  find("#result-content").scrollIntoView({ behavior: "smooth", block: "start" });
}

function setDetailLoading(loading) {
  const status = find("#detail-status");
  status.hidden = !loading && Boolean(state.detail);
  status.textContent = loading ? "正在读取场景事实……" : state.detail ? "场景事实加载完成。" : "场景加载失败。";
  find("#run-evaluation").disabled = loading || !state.detail;
}

function setEvaluationLoading(loading) {
  const button = find("#run-evaluation");
  button.disabled = loading || !state.detail;
  button.textContent = loading ? "核保 Agent 运行中……" : "运行智能核保";
  if (loading) {
    find("#evaluation-status").textContent = "正在执行七步核保流程，请稍候……";
  }
}

function clearError() {
  const panel = find("#error-panel");
  panel.hidden = true;
  panel.replaceChildren();
}

function showError(message) {
  const panel = find("#error-panel");
  panel.textContent = `操作未完成：${text(message, "未知错误")} 请检查服务状态后重试。`;
  panel.hidden = false;
}

function resetEvaluation() {
  state.evaluation = null;
  find("#result-content").hidden = true;
  [
    "#decision-summary",
    "#reason-list",
    "#action-list",
    "#rule-hit-list",
    "#evidence-list",
    "#step-timeline",
    "#tool-trace-list"
  ].forEach(selector => find(selector).replaceChildren());
  clearError();
  find("#evaluation-status").textContent = "尚未运行核保。";
}

async function selectScenario(policyNo) {
  if (!policyNo) return;

  state.detailController?.abort();
  state.evaluationController?.abort();
  state.evaluationController = null;
  const controller = new AbortController();
  state.detailController = controller;
  state.selectedPolicyNo = policyNo;
  state.detail = null;
  find("#scenario-detail").hidden = true;
  renderScenarioButtons();
  resetEvaluation();
  setDetailLoading(true);

  try {
    const detail = await requestJson(`${SCENARIO_API}/${encodeURIComponent(policyNo)}`, {
      signal: controller.signal
    });
    if (state.detailController !== controller || state.selectedPolicyNo !== policyNo) return;
    state.detail = detail;
    renderScenarioDetail(detail);
  } catch (error) {
    if (error.name !== "AbortError") {
      find("#detail-status").textContent = `场景加载失败：${text(error.message)}`;
      showError(error.message);
    }
  } finally {
    if (state.detailController === controller) {
      state.detailController = null;
      setDetailLoading(false);
    }
  }
}

async function runEvaluation() {
  if (!state.detail || state.evaluationController) return;

  const detail = state.detail;
  const controller = new AbortController();
  state.evaluationController = controller;
  clearError();
  setEvaluationLoading(true);

  try {
    const evaluation = await requestJson(EVALUATION_API, {
      method: "POST",
      signal: controller.signal,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        policyNo: detail.policyNo,
        question: detail.question
      })
    });
    if (state.evaluationController !== controller || state.detail?.policyNo !== detail.policyNo) return;
    state.evaluation = evaluation;
    renderEvaluation(evaluation, detail.expectedResult);
  } catch (error) {
    if (error.name !== "AbortError") {
      find("#evaluation-status").textContent = "核保运行失败，可以直接重试。";
      showError(error.message);
    }
  } finally {
    if (state.evaluationController === controller) {
      state.evaluationController = null;
      setEvaluationLoading(false);
    }
  }
}

async function initialize() {
  try {
    const scenarios = await requestJson(SCENARIO_API);
    if (!Array.isArray(scenarios) || scenarios.length === 0) {
      throw new Error("当前没有可用的演示场景。");
    }
    state.scenarios = scenarios;
    renderScenarioButtons();
    await selectScenario(state.scenarios[0].policyNo);
  } catch (error) {
    showError(error.message);
    find("#scenario-status").textContent = "场景目录加载失败，请刷新页面重试。";
    find("#detail-status").textContent = "服务暂时不可用。";
  }
}

find("#run-evaluation").addEventListener("click", runEvaluation);
initialize();
