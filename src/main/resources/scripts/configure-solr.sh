#!/bin/sh

# If the service URL hasn't already been set, then assume we're running the container with DMP

if [[ -z ${SOLR_SERVICE_URL} ]]
then
    # Environment variables created via: https://dmp.fabric8.io/#start-links
    export SOLR_SERVICE_URL="http://${SOLR_PORT_8983_TCP_ADDR}:${SOLR_PORT_8983_TCP_PORT}"
fi

solr_schema_url="${SOLR_SERVICE_URL}/solr/prl/schema"
solr_config_url="${SOLR_SERVICE_URL}/solr/prl/config"

# Add new field type for facets and CJK

curl -s -i -X POST -H 'Content-Type: application/json' -d '{
    "add-field-type" : {
        "name": "prl_facet",
        "class": "solr.TextField",
        "positionIncrementGap": "100",
        "analyzer": {
            "tokenizer": { "class": "solr.KeywordTokenizerFactory" },
            "filters": [ { "class": "solr.TrimFilterFactory" } ]
        }
    },
    "add-field-type": {
        "name": "text_cjk_prl",
        "class": "solr.TextField",
        "positionIncrementGap": "10000",
        "analyzer": {
            "tokenizer": { "class": "solr.ICUTokenizerFactory" },
            "filters": [
                { "class": "solr.CJKWidthFilterFactory" }
                { "class": "solr.ICUTransformFilterFactory", "id": "Traditional-Simplified" },
                { "class": "solr.ICUTransformFilterFactory", "id": "Katakana-Hiragana" },
                { "class": "solr.ICUFoldingFilterFactory" },
                { "class": "solr.CJKBigramFilterFactory", "han": true, "hiragana": true, "katakana": true, "hangul": true, "outputUnigrams": true }
            ],
            "charFilters": [
                { "class": "solr.PatternReplaceCharFilterFactory", "replacement": "$1", "pattern": "([\p{InHangul_Jamo}\p{InHangul_Compatibility_Jamo}\p{InHangul_Syllables}\p{InBopomofo}\p{InBopomofo_Extended}\p{InCJK_Compatibility}\p{InCJK_Compatibility_Forms}\p{InCJK_Compatibility_Ideographs}\p{InCJK_Compatibility_Ideographs_Supplement}\p{InCJK_Radicals_Supplement}\p{InCJK_Symbols_And_Punctuation}\p{InCJK_Unified_Ideographs}\p{InCJK_Unified_Ideographs_Extension_A}\p{InCJK_Unified_Ideographs_Extension_B}\p{InKangxi_Radicals}\p{InHalfwidth_And_Fullwidth_Forms}\p{InIdeographic_Description_Characters}])\s+(?=[\p{InBopomofo}\p{InBopomofo_Extended}\p{InCJK_Compatibility}\p{InCJK_Compatibility_Forms}\p{InCJK_Compatibility_Ideographs}\p{InCJK_Compatibility_Ideographs_Supplement}\p{InCJK_Radicals_Supplement}\p{InCJK_Symbols_And_Punctuation}\p{InCJK_Unified_Ideographs}\p{InCJK_Unified_Ideographs_Extension_A}\p{InCJK_Unified_Ideographs_Extension_B}\p{InKangxi_Radicals}\p{InHalfwidth_And_Fullwidth_Forms}\p{InIdeographic_Description_Characters}\s]*[\p{InHangul_Jamo}\p{InHangul_Compatibility_Jamo}\p{InHangul_Syllables}])" },
                { "class": "solr.PatternReplaceCharFilterFactory", "replacement": "$1", "pattern": "([\p{InHangul_Jamo}\p{InHangul_Compatibility_Jamo}\p{InHangul_Syllables}][\p{InBopomofo}\p{InBopomofo_Extended}\p{InCJK_Compatibility}\p{InCJK_Compatibility_Forms}\p{InCJK_Compatibility_Ideographs}\p{InCJK_Compatibility_Ideographs_Supplement}\p{InCJK_Radicals_Supplement}\p{InCJK_Symbols_And_Punctuation}\p{InCJK_Unified_Ideographs}\p{InCJK_Unified_Ideographs_Extension_A}\p{InCJK_Unified_Ideographs_Extension_B}\p{InKangxi_Radicals}\p{InHalfwidth_And_Fullwidth_Forms}\p{InIdeographic_Description_Characters}]*)\s+(?=[\p{InHangul_Jamo}\p{InHangul_Compatibility_Jamo}\p{InHangul_Syllables}\p{InBopomofo}\p{InBopomofo_Extended}\p{InCJK_Compatibility}\p{InCJK_Compatibility_Forms}\p{InCJK_Compatibility_Ideographs}\p{InCJK_Compatibility_Ideographs_Supplement}\p{InCJK_Radicals_Supplement}\p{InCJK_Symbols_And_Punctuation}\p{InCJK_Unified_Ideographs}\p{InCJK_Unified_Ideographs_Extension_A}\p{InCJK_Unified_Ideographs_Extension_B}\p{InKangxi_Radicals}\p{InHalfwidth_And_Fullwidth_Forms}\p{InIdeographic_Description_Characters}])" }
            ]
        }
    }
}' ${solr_schema_url}

# Institution doc fields

curl -s -i -X POST -H 'Content-Type: application/json' -d '{
    "add-field": { "name": "prrla_member_title", "type": "string", "multiValued": false, "indexed": true, "stored": true },
    "add-field": { "name": "prrla_member_description", "type": "string", "multiValued": false, "indexed": true, "stored": true },
    "add-field": { "name": "prrla_member_location", "type": "string", "multiValued": false, "indexed": true, "stored": true },
    "add-field": { "name": "prrla_member_email", "type": "string", "multiValued": false, "indexed": true, "stored": true },
    "add-field": { "name": "prrla_member_phone", "type": "string", "multiValued": false, "indexed": true, "stored": true },
    "add-field": { "name": "prrla_member_website", "type": "string", "multiValued": false, "indexed": true, "stored": true },
    "add-field": { "name": "prrla_member_latitude", "type": "string", "multiValued": false, "indexed": true, "stored": true },
    "add-field": { "name": "prrla_member_longitude", "type": "string", "multiValued": false, "indexed": true, "stored": true }
}' ${solr_schema_url}

# Item doc fields

## Text fields

curl -s -i -X POST -H 'Content-Type: application/json' -d '{
    "add-field": { "name": "text", "type": "text_general", "multiValued": true, "indexed": true, "stored": false },
    "add-field": { "name": "text_multi", "type": "text_cjk_prl", "multiValued": true, "indexed": true, "stored": false },
}' ${solr_schema_url}

## Dublin Core keyword fields

curl -s -i -X POST -H 'Content-Type: application/json' -d '{
    "add-field": { "name": "title_keyword", "type": "prl_facet", "multiValued": true, "indexed": true, "stored": true },
    "add-field": { "name": "creator_keyword", "type": "prl_facet", "multiValued": true, "indexed": true, "stored": true },
    "add-field": { "name": "subject_keyword", "type": "text_general" },
    "add-field": { "name": "description_keyword", "type": "text_general" },
    "add-field": { "name": "publisher_keyword", "type": "text_general" },
    "add-field": { "name": "contributor_keyword", "type": "text_general" },
    "add-field": { "name": "date_keyword", "type": "text_general" },
    "add-field": { "name": "type_keyword", "type": "prl_facet", "multiValued": true, "indexed": true, "stored": true },
    "add-field": { "name": "format_keyword", "type": "text_general" },
    "add-field": { "name": "identifier_keyword", "type": "text_general" },
    "add-field": { "name": "source_keyword", "type": "text_general" },
    "add-field": { "name": "language_keyword", "type": "text_general" },
    "add-field": { "name": "relation_keyword", "type": "text_general" },
    "add-field": { "name": "coverage_keyword", "type": "text_general" },
    "add-field": { "name": "rights_keyword", "type": "text_general" },
    "add-copy-field": { "source": "*_keyword", "dest": "text" },
    "add-copy-field": { "source": "*_keyword", "dest": "text_multi" },
    "add-dynamic-field": { "name": "*_keyword", "type": "string", "multiValued": true, "indexed": true, "stored": true }
}' ${solr_schema_url}

## Other fields

curl -s -i -X POST -H 'Content-Type: application/json' -d '{
    "add-field": { "name": "institutionName", "type": "prl_facet", "multiValued": false, "indexed": true, "stored": true },
    "add-field": { "name": "collectionName", "type": "prl_facet", "multiValued": true, "indexed": true, "stored": true },
    "add-field": { "name": "first_title", "type": "prl_facet", "multiValued": false, "indexed": true, "stored": true },
    "add-field": { "name": "external_link", "type": "string", "multiValued": false, "stored": true },
    "add-field": { "name": "alternate_external_link", "type": "string", "multiValued": true, "stored": true },
    "add-field": { "name": "decade", "type": "string", "multiValued": true, "indexed": true, "stored": true },
    "add-field": { "name": "sort_decade", "type": "string", "multiValued": false, "indexed": true, "stored": true },
    "add-field": { "name": "thumbnail_url", "type": "string", "multiValued": false, "indexed": true, "stored": true },
    "add-copy-field": { "source": "id", "dest": "text" },
    "add-copy-field": { "source": "id", "dest": "text_multi" },
    "add-copy-field": { "source": "institutionName", "dest": "text" },
    "add-copy-field": { "source": "institutionName", "dest": "text_multi" },
    "add-copy-field": { "source": "collectionName", "dest": "text" },
    "add-copy-field": { "source": "collectionName", "dest": "text_multi" },
}' ${solr_schema_url}

# The configuration below was necessary to get around the following CORB issue I was seeing during testing:
#
# The resource from "http://localhost:8080/solr/prl/select?q=prrla_member_title:*&rows=0&wt=json&sort=prrla_member_title%20asc&indent=true&facet=true&facet.field=prrla_member_title&json.wrf=__ng_jsonp__.__req0.finished"
# was blocked due to MIME type ("application/json") mismatch (X-Content-Type-Options: nosniff).
#
# The front-end injects the JSON-P payload into a script tag, which the browser only allows if the content type is JavaScript.

curl -s -i -X POST -H 'Content-Type: application/json' -d '{
    "create-queryresponsewriter": {
        "name": "jsonp",
        "class": "solr.JSONResponseWriter",
        "content-type": "text/javascript"
    }
}' ${solr_config_url}

# curl -s -i ${solr_schema_url}
# curl -s -i ${solr_config_url}
