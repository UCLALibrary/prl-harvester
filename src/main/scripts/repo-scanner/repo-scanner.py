#!/usr/bin/env python

import asyncio
from bs4 import BeautifulSoup
from bs4.element import Tag
import json
import sys
from tornado.gen import multi
from tornado.httpclient import AsyncHTTPClient, HTTPRequest
from urllib.parse import quote
import validators

BS4_XML_PARSER = "lxml-xml"

def list_sets_url(repository_base_url):
    '''Constructs an OAI-PMH ListSets request URL from the parameters.'''
    return "{}?verb=ListSets".format(repository_base_url)

def list_identifiers_url(repository_base_url, set_spec, metadata_prefix):
    '''Constructs an OAI-PMH ListIdentifiers request URL from the parameters.'''
    return "{}?verb=ListIdentifiers&set={}&metadataPrefix={}".format(repository_base_url, quote(set_spec), metadata_prefix)

def count_identifiers(list_identifiers_response):
    '''Determines how many identifiers belong to the set associated with the given OAI-PMH ListIdentifiers response.'''
    soup = BeautifulSoup(list_identifiers_response.body, features=BS4_XML_PARSER)

    resumption_token = soup.find("resumptionToken")

    if resumption_token is not None:
        return resumption_token["completeListSize"]
    else:
        return len(soup.find_all("header"))

async def main(argv):
    '''Scans an OAI-PMH repository and prints a report.'''
    repository_base_url = argv[0]
    http_client = AsyncHTTPClient()

    list_sets_response = await http_client.fetch(list_sets_url(repository_base_url))

    # Get the text content of each setSpec tag
    set_specs = map(Tag.get_text, BeautifulSoup(list_sets_response.body, features=BS4_XML_PARSER).find_all("setSpec"))

    # For each setSpec, construct a ListIdentifiers HTTP request
    list_identifiers_requests = map(
        lambda set_spec: (set_spec, HTTPRequest(list_identifiers_url(repository_base_url, set_spec, "oai_dc"), "GET")),
        set_specs
        )

    # Send each request (Cf. https://www.tornadoweb.org/en/branch6.3/guide/coroutines.html#parallelism)
    # For the report, we want to associate both the setSpec and request URL with the response, so Dict keys are Tuples
    list_identifiers_responses = await multi(
        { (set_spec, request.url): http_client.fetch(request) for (set_spec, request) in list_identifiers_requests }
        )

    # Print a report
    report = {
        "base_url": repository_base_url,
        "sets": {
            set_spec: {
                "url": url,
                "status_code": response.code,
                "size": count_identifiers(response) if not response.error else None
            } for ((set_spec, url), response) in list_identifiers_responses.items()
        }
    }

    print(json.dumps(report, indent=4))

if __name__ == "__main__":
    # Script accepts a single argument: an OAI-PMH repository base URL
    if (len(sys.argv) == 2 and validators.url(sys.argv[1]) is True):
        asyncio.run(main(sys.argv[1:]))
    else:
        print("Usage: {} REPOSITORY_BASE_URL".format(sys.argv[0]))
        exit(1)
