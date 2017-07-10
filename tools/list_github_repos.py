#!/usr/bin/env python
import sys
import json
from urllib.request import urlopen
from urllib.parse import urljoin

URLBASE = 'https://api.github.com/'

def get_search_url(language, minstars=100, perpage=100, page=1):
    return urljoin(
        URLBASE,
        '/search/repositories?q=language:%s+stars:>%s&page=%s&per_page=%s' %
        (language, minstars, page+1, perpage))

def get_repo_url(full_name):
    return urljoin(
        URLBASE,
        '/repos/%s/branches' % full_name)

def call_api(url):
    fp = urlopen(url)
    data = json.load(fp)
    fp.close()
    return data

def main(argv):
    npages = 10
    for page in range(npages):
        data = call_api(get_search_url('java', page=page))
        for item in data['items']:
            full_name = item['full_name']
            default_branch = item['default_branch']
            print (full_name, default_branch)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
