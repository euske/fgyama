#!/usr/bin/env python
#
# Requires: requests
#
# usage:
#   $ ./list_github_repos -L java > repos-java.tmp
#   $ ./list_github_repos repos-java.tmp > repos-java.out
#   $ cat repos-java.out
#   ReactiveX/RxJava 2.x 46ec6a6365ded7f9d96674baf40f7342d76ebdda
#   elastic/elasticsearch master b1762d69b55959d87b8ddbd5eedb9b072a8f29af
#   square/retrofit master b1ea7bad1fbddfe82412587a158d2aaa0b9f4241
#   ...
#   $ awk '{print "https://github.com/" $1 "/archive/" $3 ".zip"}' repos-java.out
#   https://github.com/ReactiveX/RxJava/archive/46ec6a6365ded7f9d96674baf40f7342d76ebdda.zip
#   https://github.com/elastic/elasticsearch/archive/b1762d69b55959d87b8ddbd5eedb9b072a8f29af.zip
#   https://github.com/square/retrofit/archive/b1ea7bad1fbddfe82412587a158d2aaa0b9f4241.zip
#   ...

import os.path
import sys
import json
import time
import requests
import logging
from urllib.parse import urljoin

URLBASE = 'https://api.github.com/'
with open(os.path.expanduser('~/.github_token')) as fp:
    TOKEN = fp.read().strip()
SESSION = requests.Session()

def get_search_url(language, minstars=100, perpage=100, page=1):
    return urljoin(
        URLBASE,
        '/search/repositories?q=language:%s+stars:>%s&page=%s&per_page=%s' %
        (language, minstars, page+1, perpage))

def get_repo_url(full_name, branch_name):
    return urljoin(
        URLBASE,
        '/repos/%s/branches/%s' % (full_name, branch_name))

def call_api(url, wait=0.5):
    logging.debug(f'call_api: {url!r}...')
    time.sleep(wait)
    headers = { 'Authorization': f'token {TOKEN}' }
    resp = SESSION.get(url, headers=headers)
    if not resp.ok:
        logging.error(f'call_api: {resp.text!r}')
        raise IOError(resp.text)
    data = resp.json()
    resp.close()
    return data

def list_repos(language, npages=10):
    for page in range(npages):
        data = call_api(get_search_url(language, page=page))
        for item in data['items']:
            full_name = item['full_name']
            default_branch = item['default_branch']
            print (full_name, default_branch)
        sys.stdout.flush()
    return

def list_commits(args):
    import fileinput
    for line in fileinput.input(args):
        (full_name, default_branch) = line.strip().split(' ')
        repo = call_api(get_repo_url(full_name, default_branch))
        commit = repo['commit']
        sha = commit['sha']
        print (full_name, default_branch, sha)
        sys.stdout.flush()
    return 0

def main(argv):
    import getopt
    def usage():
        print('usage: %s [-d] [-n npages] [-L language] [commit ...]' %
              argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dn:L:')
    except getopt.GetoptError:
        return usage()

    level = logging.INFO
    npages = 10
    language = None
    for (k, v) in opts:
        if k == '-d': level = logging.DEBUG
        elif k == '-n': npages = int(v)
        elif k == '-L': language = v

    logging.basicConfig(format='%(asctime)s %(levelname)s %(message)s', level=level)

    if language is not None:
        list_repos(language, npages=npages)
    else:
        list_commits(args)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
