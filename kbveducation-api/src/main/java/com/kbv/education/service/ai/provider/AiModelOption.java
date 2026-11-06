package com.kbv.education.service.ai.provider;

/** One model offered in the admin Settings dropdowns: the id sent to the API and a readable name. */
public record AiModelOption(String id, String label) {
}
