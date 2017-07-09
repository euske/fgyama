#!/usr/bin/env python
import sys
import json
from urllib.request import urlopen
from urllib.parse import urljoin

URLBASE = 'https://api.github.com/'

def get_search_url(language, minstars=100, perpage=100):
    return urljoin(
        URLBASE,
        '/search/repositories?q=language:%s+stars:>%s&per_page=%s' %
        (language, minstars, perpage))

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
    data = call_api(get_search_url('java'))
    for item in data['items']:
        full_name = item['full_name']
        default_branch = item['default_branch']
        print ('#', full_name, default_branch)
        url = 'https://github.com/%s/archive/%s.zip' % (full_name, default_branch)
        print (url)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
