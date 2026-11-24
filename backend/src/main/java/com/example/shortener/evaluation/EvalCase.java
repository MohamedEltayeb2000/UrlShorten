package com.example.shortener.evaluation;

import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentResult;

/** One evaluation-set entry: the input, the output under test and a second run of the same input. */
public record EvalCase(AgentInput input, AgentResult output, AgentResult rerun, boolean expectPass) {}
