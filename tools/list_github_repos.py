#!/usr/bin/env python
import os.path
import sys
import json
import time
from urllib.request import URLopener
from urllib.parse import urljoin

URLBASE = 'https://api.github.com/'
with open(os.path.expanduser('~/.github_token')) as fp:
    TOKEN = fp.read().strip()

def get_search_url(language, minstars=100, perpage=100, page=1):
    return urljoin(
        URLBASE,
        '/search/repositories?q=language:%s+stars:>%s&page=%s&per_page=%s' %
        (language, minstars, page+1, perpage))

def get_repo_url(full_name, branch_name):
    return urljoin(
        URLBASE,
        '/repos/%s/branches/%s' % (full_name, branch_name))

def call_api(url, wait=1):
    time.sleep(wait)
    req = URLopener()
    req.addheader('Authorization', 'token '+TOKEN)
    fp = req.open(url)
    data = json.load(fp)
    fp.close()
    return data

def list_repos():
    npages = 10
    for page in range(npages):
        data = call_api(get_search_url('java', page=page))
        for item in data['items']:
            full_name = item['full_name']
            default_branch = item['default_branch']
            print (full_name, default_branch)
        sys.stdout.flush()
    return

def list_commits():
    import fileinput
    for line in fileinput.input():
        (full_name, default_branch) = line.strip().split(' ')
        repo = call_api(get_repo_url(full_name, default_branch))
        commit = repo['commit']
        sha = commit['sha']
        print (full_name, default_branch, sha)
        sys.stdout.flush()
    return 0

def main(argv):
    #list_commits()
    list_repos()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
