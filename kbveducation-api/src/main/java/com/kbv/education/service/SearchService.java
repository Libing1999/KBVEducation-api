package com.kbv.education.service;

import com.kbv.education.dto.search.SearchResultItem;

import java.util.List;

public interface SearchService {

    /** Plain ILIKE search across the named entity types, capped per-type. Blank/short queries return empty. */
    List<SearchResultItem> search(String query);
}
