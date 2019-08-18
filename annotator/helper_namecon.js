// helper.ts
var CATEGORIES = [
    'a:GOOD',
    'b:ACCEPTABLE',
    'c:BAD',
    'z:UNDECIDABLE'
];
var textarea = null;
var Item = /** @class */ (function () {
    function Item() {
        this.category = '';
        this.updated = 0;
    }
    Item.prototype.load = function (cols) {
        this.category = cols[0];
        this.updated = parseInt(cols[1]);
    };
    Item.prototype.save = function () {
        return (this.category + ',' + this.updated);
    };
    return Item;
}());
function importText(data, text) {
    for (var _i = 0, _a = text.split(/\n/); _i < _a.length; _i++) {
        var line = _a[_i];
        line = line.trim();
        if (line.length == 0)
            continue;
        if (line.substr(0, 1) == '#')
            continue;
        var cols = line.split(/,/);
        if (2 <= cols.length) {
            var cid = cols.shift();
            var item = new Item();
            item.load(cols);
            data[cid] = item;
        }
    }
}
function exportText(fieldName, data) {
    var cids = Object.getOwnPropertyNames(data);
    var lines = [];
    lines.push('#START ' + fieldName);
    for (var _i = 0, _a = cids.sort(); _i < _a.length; _i++) {
        var cid = _a[_i];
        var item = data[cid];
        lines.push(cid + ',' + item.save());
    }
    lines.push('#END');
    return lines.join('\n');
}
function toArray(coll) {
    var a = [];
    for (var i = 0; i < coll.length; i++) {
        a.push(coll[i]);
    }
    return a;
}
function updateHTML(data) {
    var cids = Object.getOwnPropertyNames(data);
    for (var _i = 0, cids_1 = cids; _i < cids_1.length; _i++) {
        var cid = cids_1[_i];
        var item = data[cid];
        var sel = document.getElementById('SC' + cid);
        if (sel !== null) {
            sel.value = item.category;
        }
    }
}
function saveText(fieldName) {
    if (window.localStorage) {
        window.localStorage.setItem(fieldName, textarea.value);
    }
}
function initData(fieldName) {
    var data = {};
    function onCategoryChanged(ev) {
        var e = ev.target;
        var cid = e.id.substr(2);
        var item = data[cid];
        item.category = e.value;
        item.updated = Date.now();
        textarea.value = exportText(fieldName, data);
        saveText(fieldName);
    }
    function makeSelect(e, id, choices) {
        var select = document.createElement('select');
        select.id = id;
        for (var _i = 0, choices_1 = choices; _i < choices_1.length; _i++) {
            var k = choices_1[_i];
            var i = k.indexOf(':');
            var option = document.createElement('option');
            var v = k.substr(0, i);
            option.setAttribute('value', v);
            option.innerText = v + ') ' + k.substr(i + 1);
            select.appendChild(option);
        }
        e.appendChild(select);
        return select;
    }
    function makeCheckbox(e, id, caption) {
        var label = document.createElement('label');
        var checkbox = document.createElement('input');
        checkbox.id = id;
        checkbox.setAttribute('type', 'checkbox');
        label.appendChild(checkbox);
        label.appendChild(document.createTextNode(' ' + caption));
        e.appendChild(label);
        return checkbox;
    }
    for (var _i = 0, _a = toArray(document.getElementsByClassName('ui')); _i < _a.length; _i++) {
        var e = _a[_i];
        var cid = e.id;
        var sel1 = makeSelect(e, 'SC' + cid, CATEGORIES);
        sel1.addEventListener('change', onCategoryChanged);
        e.appendChild(document.createTextNode(' '));
        data[cid] = new Item();
    }
    return data;
}
var curdata = null;
function run(fieldName, id) {
    function onTextChanged(ev) {
        importText(curdata, textarea.value);
        updateHTML(curdata);
        saveText(fieldName);
    }
    textarea = document.getElementById(id);
    if (window.localStorage) {
        textarea.value = window.localStorage.getItem(fieldName);
    }
    textarea.addEventListener('input', onTextChanged);
    curdata = initData(fieldName);
    importText(curdata, textarea.value);
    updateHTML(curdata);
}
