// helper.ts

const CATEGORIES = [
    'a:Interchangeable',
    'b:Complementary',
    'c:Inherited',
    'd:Inclusive',
    'e:Opposite',
    'f:Same Signature',
    'g:No Relation',
    'u:Unknown'
];

var textarea: HTMLTextAreaElement = null;

class Item {

    category: string = '';
    updated: number = 0;

    load(cols: string[]) {
        this.category = cols[0];
        this.updated = parseInt(cols[1]);
    }

    save() {
        return (this.category+','+this.updated);
    }
}

interface ItemList {
    [index: string]: Item;
}

function importText(data: ItemList, text: string) {
    for (let line of text.split(/\n/)) {
        line = line.trim();
        if (line.length == 0) continue;
        if (line.substr(0,1) == '#') continue;
        let cols = line.split(/,/);
        if (2 <= cols.length) {
            let cid = cols.shift();
            let item = new Item();
            item.load(cols);
            data[cid] = item;
        }
    }
}

function exportText(fieldName: string, data: ItemList): string {
    let cids = Object.getOwnPropertyNames(data);
    let lines = [] as string[];
    lines.push('#START '+fieldName);
    for (let cid of cids.sort()) {
        let item = data[cid];
        lines.push(cid+','+item.save());
    }
    lines.push('#END');
    return lines.join('\n');
}

function toArray(coll: HTMLCollectionOf<Element>): Element[] {
    let a = [] as Element[];
    for (let i = 0; i < coll.length; i++) {
        a.push(coll[i]);
    }
    return a;
}

function updateHTML(data: ItemList) {
    let cids = Object.getOwnPropertyNames(data);
    for (let cid of cids) {
        let item = data[cid];
        let sel = document.getElementById('SC'+cid) as HTMLSelectElement;
        if (sel !== null) {
            sel.value = item.category;
        }
    }
}

function saveText(fieldName: string) {
    if (window.localStorage) {
        window.localStorage.setItem(fieldName, textarea.value);
    }
}

function initData(fieldName: string): ItemList {
    let data: ItemList = {};

    function onCategoryChanged(ev: Event) {
        let e = ev.target as HTMLSelectElement;
        let cid = e.id.substr(2);
        let item = data[cid];
        item.category = e.value;
        item.updated = Date.now();
        textarea.value = exportText(fieldName, data);
	saveText(fieldName);
    }

    function makeSelect(e: Element, id: string, choices: string[]) {
        let select = document.createElement('select');
        select.id = id;
        for (let k of choices) {
            let i = k.indexOf(':');
            let option = document.createElement('option');
            let v = k.substr(0, i);
            option.setAttribute('value', v);
            option.innerText = v+') '+k.substr(i+1);
            select.appendChild(option);
        }
        e.appendChild(select);
        return select;
    }

    function makeCheckbox(e: Element, id: string, caption: string) {
        let label = document.createElement('label');
        let checkbox = document.createElement('input');
        checkbox.id = id;
        checkbox.setAttribute('type', 'checkbox');
        label.appendChild(checkbox);
        label.appendChild(document.createTextNode(' '+caption));
        e.appendChild(label);
        return checkbox;
    }

    for (let e of toArray(document.getElementsByClassName('ui'))) {
        let cid = e.id;
        let sel1 = makeSelect(e, 'SC'+cid, CATEGORIES);
        sel1.addEventListener('change', onCategoryChanged);
        e.appendChild(document.createTextNode(' '));
        data[cid] = new Item();
    }
    return data;
}

var curdata: ItemList = null;
function run(fieldName: string, id: string) {
    function onTextChanged(ev: Event) {
        importText(curdata, textarea.value);
        updateHTML(curdata);
	saveText(fieldName);
    }
    textarea = document.getElementById(id) as HTMLTextAreaElement;
    if (window.localStorage) {
        textarea.value = window.localStorage.getItem(fieldName);
    }
    textarea.addEventListener('input', onTextChanged);
    curdata = initData(fieldName);
    importText(curdata, textarea.value);
    updateHTML(curdata);
}
