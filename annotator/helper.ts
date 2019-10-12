///  helper.ts
///  Evaluation helper app.
///


//  Utility functions.
//
function toArray(coll: HTMLCollectionOf<Element>): Element[] {
    let a = [] as Element[];
    for (let i = 0; i < coll.length; i++) {
        a.push(coll[i]);
    }
    return a;
}


//  Item
//
class Item {

    cid: string;
    choice: string = '';
    updated: number = 0;
    comment: string = '';

    constructor(cid: string) {
	this.cid = cid;
    }

    setChoice(choice: string) {
	this.choice = choice;
        this.updated = Date.now();
    }

    setComment(comment: string) {
	this.comment = comment;
        this.updated = Date.now();
    }

    load(obj: any) {
        this.choice = obj['choice']
        this.updated = obj['updated']
	this.comment = obj['comment']
    }

    save() {
	let obj = {
	    'cid': this.cid,
	    'choice': this.choice,
	    'updated': this.updated,
	    'comment': this.comment,
	}
	return obj;
    }
}


//  ItemList
//
interface ItemList {
    [index: string]: Item;
}


//  ItemDB
//
class ItemDB {

    textarea: HTMLTextAreaElement;
    fieldName: string;
    items: ItemList;

    constructor(textarea: HTMLTextAreaElement, fieldName: string) {
	this.textarea = textarea;
	this.fieldName = fieldName;
	this.items = {};
    }

    init() {
	for (let e of toArray(document.getElementsByClassName('ui'))) {
            let cid = e.id;
            let item = new Item(cid);
            this.items[cid] = item;
            for (let sel of toArray(e.getElementsByTagName('select'))) {
                sel.addEventListener(
		    'change', (ev) => { this.onChoiceChanged(item, ev); });
            }
            for (let fld of toArray(e.getElementsByTagName('input'))) {
                fld.addEventListener(
		    'change', (ev) => { this.onCommentChanged(item, ev); });
            }
	}
	this.loadTextArea();
	this.textarea.addEventListener(
	    'input', (ev) => { this.onTextChanged(ev); });
	this.importData();
	this.updateHTML();
	console.info("init");
    }

    onTextChanged(ev: Event) {
        this.importData();
        this.updateHTML();
	this.saveTextArea();
	console.info("onTextChanged");
    }

    onChoiceChanged(item: Item, ev: Event) {
        let e = ev.target as HTMLSelectElement;
	item.setChoice(e.value);
        this.exportData();
	this.saveTextArea();
	console.info("onChoiceChanged: ", item);
    }

    onCommentChanged(item: Item, ev: Event) {
        let e = ev.target as HTMLInputElement;
	item.setComment(e.value);
        this.exportData();
	this.saveTextArea();
	console.info("onCommentChanged: ", item);
    }

    saveTextArea() {
	if (window.localStorage) {
            window.localStorage.setItem(this.fieldName, this.textarea.value);
	}
    }

    loadTextArea() {
	if (window.localStorage) {
            this.textarea.value = window.localStorage.getItem(this.fieldName);
	}
    }

    importData() {
	let text = this.textarea.value;
	for (let line of text.split(/\n/)) {
            line = line.trim();
            if (line.length == 0) continue;
            if (line.substr(0,1) == '#') continue;
	    let obj = JSON.parse(line);
            let cid = obj['cid'];
            let item = this.items[cid];
            item.load(obj);
	}
    }

    exportData() {
	let cids = Object.getOwnPropertyNames(this.items);
	let lines = [] as string[];
	lines.push('#START '+this.fieldName);
	for (let cid of cids.sort()) {
            let item = this.items[cid];
	    let obj = item.save();
            lines.push(JSON.stringify(obj));
	}
	lines.push('#END');
	this.textarea.value = lines.join('\n');
    }

    updateHTML() {
	for (let e of toArray(document.getElementsByClassName('ui'))) {
            let cid = e.id;
            let item = this.items[cid];
            if (item === undefined) continue;
            for (let sel of toArray(e.getElementsByTagName('select'))) {
                if (item.choice) {
		    (sel as HTMLSelectElement).value = item.choice;
                }
            }
            for (let fld of toArray(e.getElementsByTagName('input'))) {
		(fld as HTMLInputElement).value = item.comment;
            }
	}
    }
}

// main
function run(id: string, fieldName: string) {
    let textarea = document.getElementById(id) as HTMLTextAreaElement;
    let db = new ItemDB(textarea, fieldName);
    db.init();
}
