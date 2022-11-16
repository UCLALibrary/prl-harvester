#!/bin/bash

# This script closely follows the "Custom Set-Up Scripts" example here:
# https://solr.apache.org/guide/solr/9_0/deployment-guide/solr-in-docker.html#custom-set-up-scripts

precreate-core prl && \

# See: https://solr.apache.org/guide/solr/9_0/indexing-guide/tokenizers.html#icu-tokenizer
mkdir /var/solr/data/lib && \
ln -s -t /var/solr/data/lib \
    /opt/solr/modules/analysis-extras/lib/{solr-analysis-extras-*.jar,icu4j-*.jar,lucene-analysis-icu-*.jar} && \

cp /root/{managed-schema.xml,solrconfig.xml} /var/solr/data/prl/conf/ && \

solr-foreground
