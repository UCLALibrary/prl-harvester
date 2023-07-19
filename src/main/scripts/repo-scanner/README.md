# repo-scanner

A script for determining the harvest-ability of each set of an OAI-PMH repository with the following problem: it sends HTTP responses that are not compliant with the spec.

## Recommended setup

Tested with Python 3.10.6.

```bash
#!/bin/bash

python3 -m venv venv_repo_scanner && . $_/bin/activate
# (exit virtual environment later with `deactivate`)
pip install -r requirements.txt
```

## Example usage

To get a complete report:

```bash
./repo-scanner.py https://digital.library.ucla.edu/catalog/oai
```

To get a comma-separated list of harvest-able sets for including in a harvest job, use a JSON processor like [jq](https://jqlang.github.io/jq/):

```bash
./repo-scanner.py https://digital.library.ucla.edu/catalog/oai \
  | jq --raw-output --join-output '.sets | map_values(select(.status_code == 200)) | keys[] + ","'
```
