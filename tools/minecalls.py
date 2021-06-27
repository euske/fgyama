#!/usr/bin/env python
import sys
import logging
from graphs import DFType, parsemethodname, parserefname
from graph2index import GraphDB
from algos import SCC, Cons

def gettail(name):
    (_,_,name) = name.rpartition('/')
    return name.lower()

KINDS = {
    'value', 'valueset',
    'op_assign', 'op_prefix', 'op_infix', 'op_postfix',
    'op_typecast', 'op_typecheck',
    'ref_var', 'ref_array', 'ref_field',
    'call', 'new',
}
VALUES = {'value', 'valueset'}
CALLS = {'call', 'new'}
REFS = {'ref_var', 'ref_field', 'ref_array'}
CONDS = {'join', 'begin', 'end', 'case', 'catchjoin'}
def ignored(n):
    return (len(n.inputs) == 1 and n.kind not in KINDS)

def getfeats(label, n):
    if n.kind not in KINDS: return
    if label.startswith('@'):
        label = '@'
    if n.kind in VALUES:
        (_,t) = DFType.parse(n.ntype)
        yield f'{label}:{n.kind}:{t}'
    elif n.kind in REFS:
        yield f'{label}:{n.kind}:{parserefname(n.ref)}'
    elif n.kind in CALLS:
        methods = n.data.split()
        (_,name,_) = parsemethodname(methods[0])
        yield f'{label}:{n.kind}:{name}'
    else:
        yield f'{label}:{n.kind}:{n.data}'
    return

def visit(out, label0, n0, visited, n=0):
    global maxpathlen
    if n0 in visited: return
    visited.add(n0)
    if ignored(n0):
        for (label1,n1) in n0.inputs.items():
            if label1 == '#bypass': continue
            if label1.startswith('_'): continue
            visit(out, label0, n1, visited, n)
    else:
        for feat in getfeats(label0, n0):
            out.add((n, feat))
        n += 1
        if n < maxpathlen:
            for (label1,n1) in n0.inputs.items():
                if label1.startswith('_'): continue
                if label1 == '#bypass': continue
                if n1.kind == 'ref_array' and not label1: continue
                visit(out, label1, n1, visited, n)

funargs_path = {
    # new java.io.File(path)
    'Ljava/io/File;.<init>(Ljava/lang/String;)V': ['#arg0'],
    # new java.io.FileReader(path)
    'Ljava/io/FileReader;.<init>(Ljava/lang/String;)V': ['#arg0'],
    # new java.io.FileWriter(path)
    'Ljava/io/FileWriter;.<init>(Ljava/lang/String;)V': ['#arg0'],
    # new java.io.FileWriter(path, boolean)
    'Ljava/io/FileWriter;.<init>(Ljava/lang/String;Z)V': ['#arg0'],
    # new java.io.FileInputStream(path)
    'Ljava/io/FileInputStream;.<init>(Ljava/lang/String;)V': ['#arg0'],
    # new java.io.FileOutputStream(path)
    'Ljava/io/FileOutputStream;.<init>(Ljava/lang/String;)V': ['#arg0'],
    # new java.io.FileOutputStream(path, boolean)
    'Ljava/io/FileOutputStream;.<init>(Ljava/lang/String;Z)V': ['#arg0'],
}

funargs_url = {
    # new java.net.URI(url)
    'Ljava/net/URI;.<init>(Ljava/lang/String;)V': ['#arg0'],
    # java.net.URI.create(url)
    'Ljava/net/URI;.create(Ljava/lang/String;)Ljava/net/URI;': ['#arg0'],
    # java.net.URI.resolve(url)
    'Ljava/net/URI;.resolve(Ljava/lang/String;)Ljava/net/URI;': ['#arg0'],
    # new java.net.URL(url)
    'Ljava/net/URL;.<init>(Ljava/lang/String;)V': ['#arg0'],
}

funargs_sql = {
    # java.sql.Statement.addBatch(sql)
    'Ljava/sql/Statement;.addBatch(Ljava/lang/String;)V': ['#arg0'],
    # java.sql.Statement.execute(sql)
    'Ljava/sql/Statement;.execute(Ljava/lang/String;)Z': ['#arg0'],
    # java.sql.Statement.execute(sql, autogen)
    'Ljava/sql/Statement;.execute(Ljava/lang/String;I)Z': ['#arg0'],
    # java.sql.Statement.execute(sql, columnindexes)
    'Ljava/sql/Statement;.execute(Ljava/lang/String;[I)Z': ['#arg0'],
    # java.sql.Statement.execute(sql, columnnames)
    'Ljava/sql/Statement;.execute(Ljava/lang/String;[Ljava/lang/String;)Z': ['#arg0'],
    # java.sql.Statement.executeQuery(sql)
    'Ljava/sql/Statement;.executeQuery(Ljava/lang/String;)Ljava/sql/ResultSet;': ['#arg0'],
    # java.sql.Statement.executeUpdate(sql)
    'Ljava/sql/Statement;.executeUpdate(Ljava/lang/String;)I': ['#arg0'],
    # java.sql.Statement.executeUpdate(sql, autogen)
    'Ljava/sql/Statement;.executeUpdate(Ljava/lang/String;I)I': ['#arg0'],
    # java.sql.Statement.executeUpdate(sql, columnindexes)
    'Ljava/sql/Statement;.executeUpdate(Ljava/lang/String;[I)I': ['#arg0'],
    # java.sql.Statement.executeUpdate(sql, columnnames)
    'Ljava/sql/Statement;.executeUpdate(Ljava/lang/String;[Ljava/lang/String;)I': ['#arg0'],
}

funargs_host = {
    # java.net.InetAddress.getAllByName(host)
    'Ljava/net/InetAddress;.getAllByName(Ljava/lang/String;)[Ljava/net/InetAddress;': ['#arg0'],
    # java.net.InetAddress.getByAddress(host, addr)
    'Ljava/net/InetAddress;.getByAddress(Ljava/lang/String;[B)Ljava/net/InetAddress;': ['#arg0'],
    # java.net.InetAddress.getByName(host)
    'Ljava/net/InetAddress;.getByName(Ljava/lang/String;)Ljava/net/InetAddress;': ['#arg0'],
    # new java.net.InetSocketAddress(host, port)
    'Ljava/net/InetSocketAddress;.<init>(Ljava/lang/String;I)V': ['#arg0'],
    # java.net.InetSocketAddress.createUnresolved(host, port)
    'Ljava/net/InetSocketAddress;createUnresolved(Ljava/lang/String;I)Ljava/net/InetSocketAddress;': ['#arg0'],

    # new java.net.Socket(host, port)
    'Ljava/net/Socket;.<init>(Ljava/lang/String;I)V': ['#arg0'],
    # new java.net.Socket(host, port, stream)
    'Ljava/net/Socket;.<init>(Ljava/lang/String;IZ)V': ['#arg0'],
    # new java.net.Socket(host, port, localaddr, localport)
    'Ljava/net/Socket;.<init>(Ljava/lang/String;ILjava/net/InetAddress;I)V': ['#arg0'],

    # new java.net.SSLSocket(host, port)
    'Ljavax/net/ssl/SSLSocket;.<init>(Ljava/lang/String;I)V': ['#arg0'],
    # new java.net.SSLSocket(host, port, clientaddr, clientport)
    'Ljavax/net/ssl/SSLSocket;.<init>(Ljava/lang/String;ILjava/net/InetAddress;I)V': ['#arg0'],

    # new java.net.URI(scheme, userinfo, host, port, path, query, fragment)
    'Ljava/net/URI;.<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V': ['#arg2'],
    # new java.net.URI(scheme, host, path, fragment)
    'Ljava/net/URI;.<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V': ['#arg1'],
    # new java.net.URL(protocol, host, port, file)
    'Ljava/net/URL;.<init>(Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;)V': ['#arg1'],
    # new java.net.URL(protocol, host, port, file, handler)
    'Ljava/net/URL;.<init>(Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/net/URLStreamHandler;)V': ['#arg1'],
    # new java.net.URL(protocol, host, file)
    'Ljava/net/URL;.<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V': ['#arg1'],
    # java.net.URL.set(protocol, host, port, file, ref)
    'Ljava/net/URL;.set(Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;)V': ['#arg1'],
    # java.net.URL.set(protocol, host, port, authority, userinfo, path, query, ref)
    'Ljava/net/URL;.set(Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V': ['#arg1'],
}

funargs_port = {
    # new java.net.InetSocketAddress(port)
    'Ljava/net/InetSocketAddress;.<init>(I)V': ['#arg0'],
    # new java.net.InetSocketAddress(host, port)
    'Ljava/net/InetSocketAddress;.<init>(Ljava/lang/String;I)V': ['#arg1'],
    # new java.net.InetSocketAddress(addr, port)
    'Ljava/net/InetSocketAddress;.<init>(Ljava/net/InetAddress;I)V': ['#arg1'],
    # java.net.InetSocketAddress.createUnresolved(host, port)
    'Ljava/net/InetSocketAddress;createUnresolved(Ljava/lang/String;I)Ljava/net/InetSocketAddress;': ['#arg1'],

    # new java.net.Socket(host, port)
    'Ljava/net/Socket;.<init>(Ljava/lang/String;I)V': ['#arg1'],
    # new java.net.Socket(addr, port)
    'Ljava/net/Socket;.<init>(Ljava/net/InetAddress;I)V': ['#arg1'],
    # new java.net.Socket(host, port, stream)
    'Ljava/net/Socket;.<init>(Ljava/lang/String;IZ)V': ['#arg1'],
    # new java.net.Socket(addr, port, stream)
    'Ljava/net/Socket;.<init>(Ljava/net/InetAddress;IZ)V': ['#arg1'],
    # new java.net.Socket(host, port, localaddr, localport)
    'Ljava/net/Socket;.<init>(Ljava/lang/String;ILjava/net/InetAddress;I)V': ['#arg1', '#arg3'],
    # new java.net.Socket(addr, port, localaddr, localport)
    'Ljava/net/Socket;.<init>(Ljava/net/InetAddress;ILjava/net/InetAddress;I)V': ['#arg1', '#arg3'],

    # new java.net.ServerSocket(port)
    'Ljava/net/ServerSocket;.<init>(I)V': ['#arg0'],
    # new java.net.ServerSocket(port, backlog)
    'Ljava/net/ServerSocket;.<init>(II)V': ['#arg0'],
    # new java.net.ServerSocket(port, backlog, addr)
    'Ljava/net/ServerSocket;.<init>(IILjava/net/InetAddress;)V': ['#arg0'],

    # new java.net.SSLSocket(host, port)
    'Ljavax/net/ssl/SSLSocket;.<init>(Ljava/lang/String;I)V': ['#arg1'],
    # new java.net.SSLSocket(addr, port)
    'Ljavax/net/ssl/SSLSocket;.<init>(Ljava/net/InetAddress;I)V': ['#arg1'],
    # new java.net.SSLSocket(host, port, clientaddr, clientport)
    'Ljavax/net/ssl/SSLSocket;.<init>(Ljava/lang/String;ILjava/net/InetAddress;I)V': ['#arg1', '#arg3'],
    # new java.net.SSLSocket(addr, port, clientaddr, clientport)
    'Ljavax/net/ssl/SSLSocket;.<init>(Ljava/net/InetAddress;ILjava/net/InetAddress;I)V': ['#arg1', '#arg3'],

    # new java.net.DatagramSocket(port)
    'Ljava/net/DatagramSocket;.<init>(I)V': ['#arg0'],
    # new java.net.DatagramSocket(port, addr)
    'Ljava/net/DatagramSocket;.<init>(ILjava/net/InetAddress;)V': ['#arg0'],
    # java.net.DatagramSocket.connect(port, addr)
    'Ljava/net/DatagramSocket;connect(Ljava/net/InetAddress;I)V': ['#arg0'],

}

funargs_xcoord = {
    # new java.awt.Point(x, y)
    'Ljava/awt/Point;.<init>(II)V': ['#arg0'],
    # java.awt.Point.setLocation(x, y)
    'Ljava/awt/Point;.setLocation(II)V': ['#arg0'],
    # java.awt.Point.setLocation(x, y)
    'Ljava/awt/Point;.setLocation(DD)V': ['#arg0'],
    # java.awt.Point.move(x, y)
    'Ljava/awt/Point;.move(II)V': ['#arg0'],

    # new java.awt.Rectangle(x, y, width, height)
    'Ljava/awt/Rectangle;.<init>(IIII)V': ['#arg0'],
    # java.awt.Rectangle.add(x, y)
    'Ljava/awt/Rectangle;.add(II)V': ['#arg0'],
    # java.awt.Rectangle.contains(x, y)
    'Ljava/awt/Rectangle;.contains(II)Z': ['#arg0'],
    # java.awt.Rectangle.contains(x, y, W, H)
    'Ljava/awt/Rectangle;.contains(IIII)Z': ['#arg0'],
    # java.awt.Rectangle.inside(x, y)
    'Ljava/awt/Rectangle;.contains(II)Z': ['#arg0'],
    # java.awt.Rectangle.move(x, y)
    'Ljava/awt/Rectangle;.move(II)V': ['#arg0'],
    # java.awt.Rectangle.reshape(x, y, width, height)
    'Ljava/awt/Rectangle;.reshape(IIII)V': ['#arg0'],
    # java.awt.Rectangle.setBounds(x, y, width, height)
    'Ljava/awt/Rectangle;.setBounds(IIII)V': ['#arg0'],
    # java.awt.Rectangle.setLocation(x, y)
    'Ljava/awt/Rectangle;.setLocation(II)V': ['#arg0'],
    # java.awt.Rectangle.setRect(x, y, width, height)
    'Ljava/awt/Rectangle;.setRect(DDDD)V': ['#arg0'],

    # java.awt.Component.contains(x, y)
    'Ljava/awt/Component;.contains(II)Z': ['#arg0'],
    # java.awt.Component.inside(x, y)
    'Ljava/awt/Component;.inside(II)Z': ['#arg0'],
    # java.awt.Component.getComponentAt(x, y)
    'Ljava/awt/Component;.getComponentAt(II)Ljava/awt/Component;': ['#arg0'],
    # java.awt.Component.locate(x, y)
    'Ljava/awt/Component;.locate(II)Ljava/awt/Component;': ['#arg0'],
    # java.awt.Component.setLocation(x, y)
    'Ljava/awt/Component;.setLocation(II)V': ['#arg0'],
    # java.awt.Component.move(x, y)
    'Ljava/awt/Component;.move(II)V': ['#arg0'],
    # java.awt.Component.setBounds(x, y, width, height)
    'Ljava/awt/Component;.setBounds(IIII)V': ['#arg0'],
    # java.awt.Component.reshape(x, y, width, height)
    'Ljava/awt/Component;.reshape(IIII)V': ['#arg0'],
    # java.awt.Component.repaint(x, y, width, height)
    'Ljava/awt/Component;.repaint(IIII)V': ['#arg0'],
    # java.awt.Component.repaint(tm, x, y, width, height)
    'Ljava/awt/Component;.repaint(JIIII)V': ['#arg1'],
    # java.awt.Component.imageUpdate(image, flags, x, y, w, h)
    'Ljava/awt/Component;.imageUpdate(Ljava/awt/Image;IIIII)Z': ['#arg2'],

    # java.awt.Container.findComponentAt(x, y)
    'Ljava/awt/Container;.findComponentAt(II)Ljava/awt/Component;': ['#arg0'],
}

funargs_ycoord = {
    # new java.awt.Point(x, y)
    'Ljava/awt/Point;.<init>(II)V': ['#arg1'],
    # java.awt.Point.setLocation(x, y)
    'Ljava/awt/Point;.setLocation(II)V': ['#arg1'],
    # java.awt.Point.setLocation(x, y)
    'Ljava/awt/Point;.setLocation(DD)V': ['#arg1'],
    # java.awt.Point.move(x, y)
    'Ljava/awt/Point;.move(II)V': ['#arg1'],

    # new java.awt.Rectangle(x, y, width, height)
    'Ljava/awt/Rectangle;.<init>(IIII)V': ['#arg1'],
    # java.awt.Rectangle.add(x, y)
    'Ljava/awt/Rectangle;.add(II)V': ['#arg1'],
    # java.awt.Rectangle.contains(x, y)
    'Ljava/awt/Rectangle;.contains(II)Z': ['#arg1'],
    # java.awt.Rectangle.contains(x, y, W, H)
    'Ljava/awt/Rectangle;.contains(IIII)Z': ['#arg1'],
    # java.awt.Rectangle.inside(x, y)
    'Ljava/awt/Rectangle;.contains(II)Z': ['#arg1'],
    # java.awt.Rectangle.move(x, y)
    'Ljava/awt/Rectangle;.move(II)V': ['#arg1'],
    # java.awt.Rectangle.reshape(x, y, width, height)
    'Ljava/awt/Rectangle;.reshape(IIII)V': ['#arg1'],
    # java.awt.Rectangle.setBounds(x, y, width, height)
    'Ljava/awt/Rectangle;.setBounds(IIII)V': ['#arg1'],
    # java.awt.Rectangle.setLocation(x, y)
    'Ljava/awt/Rectangle;.setLocation(II)V': ['#arg1'],
    # java.awt.Rectangle.setRect(x, y, width, height)
    'Ljava/awt/Rectangle;.setRect(DDDD)V': ['#arg1'],

    # java.awt.Component.contains(x, y)
    'Ljava/awt/Component;.contains(II)Z': ['#arg1'],
    # java.awt.Component.inside(x, y)
    'Ljava/awt/Component;.inside(II)Z': ['#arg1'],
    # java.awt.Component.getComponentAt(x, y)
    'Ljava/awt/Component;.getComponentAt(II)Ljava/awt/Component;': ['#arg1'],
    # java.awt.Component.locate(x, y)
    'Ljava/awt/Component;.locate(II)Ljava/awt/Component;': ['#arg1'],
    # java.awt.Component.setLocation(x, y)
    'Ljava/awt/Component;.setLocation(II)V': ['#arg1'],
    # java.awt.Component.move(x, y)
    'Ljava/awt/Component;.move(II)V': ['#arg1'],
    # java.awt.Component.setBounds(x, y, width, height)
    'Ljava/awt/Component;.setBounds(IIII)V': ['#arg1'],
    # java.awt.Component.reshape(x, y, width, height)
    'Ljava/awt/Component;.reshape(IIII)V': ['#arg1'],
    # java.awt.Component.repaint(x, y, width, height)
    'Ljava/awt/Component;.repaint(IIII)V': ['#arg1'],
    # java.awt.Component.repaint(tm, x, y, width, height)
    'Ljava/awt/Component;.repaint(JIIII)V': ['#arg2'],
    # java.awt.Component.imageUpdate(image, flags, x, y, w, h)
    'Ljava/awt/Component;.imageUpdate(Ljava/awt/Image;IIIII)Z': ['#arg3'],

    # java.awt.Container.findComponentAt(x, y)
    'Ljava/awt/Container;.findComponentAt(II)Ljava/awt/Component;': ['#arg1'],
}

funargs_width = {
    # new java.awt.Dimension(width, height)
    'Ljava/awt/Dimension;.<init>(II)V': ['#arg0'],
    # java.awt.Dimension.setSize(width, height)
    'Ljava/awt/Dimension;.setSize(II)V': ['#arg0'],
    # java.awt.Dimension.setSize(width, height)
    'Ljava/awt/Dimension;.setSize(DD)V': ['#arg0'],

    # new java.awt.Rectangle(width, height)
    'Ljava/awt/Rectangle;.<init>(II)V': ['#arg0'],
    # new java.awt.Rectangle(x, y, width, height)
    'Ljava/awt/Rectangle;.<init>(IIII)V': ['#arg2'],
    # java.awt.Rectangle.resize(width, height)
    'Ljava/awt/Rectangle;.reshape(II)V': ['#arg0'],
    # java.awt.Rectangle.contains(x, y, W, H)
    'Ljava/awt/Rectangle;.contains(IIII)Z': ['#arg2'],
    # java.awt.Rectangle.reshape(x, y, width, height)
    'Ljava/awt/Rectangle;.reshape(IIII)V': ['#arg2'],
    # java.awt.Rectangle.setBounds(x, y, width, height)
    'Ljava/awt/Rectangle;.setBounds(IIII)V': ['#arg2'],
    # java.awt.Rectangle.setSize(width, height)
    'Ljava/awt/Rectangle;.setSize(II)V': ['#arg0'],
    # java.awt.Rectangle.setRect(x, y, width, height)
    'Ljava/awt/Rectangle;.setRect(DDDD)V': ['#arg2'],

    # java.awt.Component.createImage(width, height)
    'Ljava/awt/Component;.createImage(II)Ljava/awt/Image;': ['#arg0'],
    # java.awt.Component.createVolatileImage(width, height)
    'Ljava/awt/Component;.createVolatileImage(II)Ljava/awt/image/VolatileImage;': ['#arg0'],
    # java.awt.Component.createVolatileImage(width, height, caps)
    'Ljava/awt/Component;.createVolatileImage(IILjava/awt/ImageCapabilities;)Ljava/awt/image/VolatileImage;': ['#arg0'],
    # java.awt.Component.checkImage(image, width, height, observer)
    'Ljava/awt/Component;.checkImage(Ljava/awt/Image;IILjava/awt/image/ImageObserver;)I': ['#arg1'],
    # java.awt.Component.prepareImage(image, width, height, observer)
    'Ljava/awt/Component;.prepareImage(Ljava/awt/Image;IILjava/awt/image/ImageObserver;)Z': ['#arg1'],
    # java.awt.Component.getBaseline(width, height)
    'Ljava/awt/Component;.getBaseline(II)I': ['#arg0'],
    # java.awt.Component.setSize(width, height)
    'Ljava/awt/Component;.setSize(II)V': ['#arg0'],
    # java.awt.Component.resize(width, height)
    'Ljava/awt/Component;.resize(II)V': ['#arg0'],
    # java.awt.Component.setBounds(x, y, width, height)
    'Ljava/awt/Component;.setBounds(IIII)V': ['#arg2'],
    # java.awt.Component.reshape(x, y, width, height)
    'Ljava/awt/Component;.reshape(IIII)V': ['#arg2'],
    # java.awt.Component.repaint(x, y, width, height)
    'Ljava/awt/Component;.repaint(IIII)V': ['#arg2'],
    # java.awt.Component.repaint(tm, x, y, width, height)
    'Ljava/awt/Component;.repaint(JIIII)V': ['#arg3'],
    # java.awt.Component.imageUpdate(image, flags, x, y, w, h)
    'Ljava/awt/Component;.imageUpdate(Ljava/awt/Image;IIIII)Z': ['#arg4'],
}

funargs_height = {
    # new java.awt.Dimension(width, height)
    'Ljava/awt/Dimension;.<init>(II)V': ['#arg1'],
    # java.awt.Dimension.setSize(width, height)
    'Ljava/awt/Dimension;.setSize(II)V': ['#arg1'],
    # java.awt.Dimension.setSize(width, height)
    'Ljava/awt/Dimension;.setSize(DD)V': ['#arg1'],

    # new java.awt.Rectangle(width, height)
    'Ljava/awt/Rectangle;.<init>(II)V': ['#arg1'],
    # new java.awt.Rectangle(x, y, width, height)
    'Ljava/awt/Rectangle;.<init>(IIII)V': ['#arg3'],
    # java.awt.Rectangle.resize(width, height)
    'Ljava/awt/Rectangle;.reshape(II)V': ['#arg1'],
    # java.awt.Rectangle.contains(x, y, W, H)
    'Ljava/awt/Rectangle;.contains(IIII)Z': ['#arg3'],
    # java.awt.Rectangle.reshape(x, y, width, height)
    'Ljava/awt/Rectangle;.reshape(IIII)V': ['#arg3'],
    # java.awt.Rectangle.setBounds(x, y, width, height)
    'Ljava/awt/Rectangle;.setBounds(IIII)V': ['#arg3'],
    # java.awt.Rectangle.setSize(width, height)
    'Ljava/awt/Rectangle;.setSize(II)V': ['#arg1'],
    # java.awt.Rectangle.setRect(x, y, width, height)
    'Ljava/awt/Rectangle;.setRect(DDDD)V': ['#arg3'],

    # java.awt.Component.createImage(width, height)
    'Ljava/awt/Component;.createImage(II)Ljava/awt/Image;': ['#arg1'],
    # java.awt.Component.createVolatileImage(width, height)
    'Ljava/awt/Component;.createVolatileImage(II)Ljava/awt/image/VolatileImage;': ['#arg1'],
    # java.awt.Component.createVolatileImage(width, height, caps)
    'Ljava/awt/Component;.createVolatileImage(IILjava/awt/ImageCapabilities;)Ljava/awt/image/VolatileImage;': ['#arg1'],
    # java.awt.Component.checkImage(image, width, height, observer)
    'Ljava/awt/Component;.checkImage(Ljava/awt/Image;IILjava/awt/image/ImageObserver;)I': ['#arg2'],
    # java.awt.Component.prepareImage(image, width, height, observer)
    'Ljava/awt/Component;.prepareImage(Ljava/awt/Image;IILjava/awt/image/ImageObserver;)Z': ['#arg2'],
    # java.awt.Component.getBaseline(width, height)
    'Ljava/awt/Component;.getBaseline(II)I': ['#arg1'],
    # java.awt.Component.setSize(width, height)
    'Ljava/awt/Component;.setSize(II)V': ['#arg1'],
    # java.awt.Component.resize(width, height)
    'Ljava/awt/Component;.resize(II)V': ['#arg1'],
    # java.awt.Component.setBounds(x, y, width, height)
    'Ljava/awt/Component;.setBounds(IIII)V': ['#arg3'],
    # java.awt.Component.reshape(x, y, width, height)
    'Ljava/awt/Component;.reshape(IIII)V': ['#arg3'],
    # java.awt.Component.repaint(x, y, width, height)
    'Ljava/awt/Component;.repaint(IIII)V': ['#arg3'],
    # java.awt.Component.repaint(tm, x, y, width, height)
    'Ljava/awt/Component;.repaint(JIIII)V': ['#arg4'],
    # java.awt.Component.imageUpdate(image, flags, x, y, w, h)
    'Ljava/awt/Component;.imageUpdate(Ljava/awt/Image;IIIII)Z': ['#arg5'],
}

funargs = {}
def add(label, args):
    for (k,v) in args.items():
        if k in funargs:
            a = funargs[k]
        else:
            a = funargs[k] = []
        a.extend( (label, x) for x in v )
    return
add('path', funargs_path) # String
add('url', funargs_url)   # String
add('sql', funargs_sql)   # String
add('host', funargs_host) # String
add('port', funargs_port) # int
add('xcoord', funargs_xcoord) # int
add('ycoord', funargs_ycoord) # int
add('width', funargs_width) # int
add('height', funargs_height) # int


# main
def main(argv):
    global maxpathlen
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-M maxoverrides] [-m maxpathlen] graph.db')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dM:m:')
    except getopt.GetoptError:
        return usage()
    level = logging.INFO
    maxoverrides = 1
    maxpathlen = 5
    for (k, v) in opts:
        if k == '-d': level = logging.DEBUG
        elif k == '-M': maxoverrides = int(v)
        elif k == '-m': maxpathlen = int(v)
    if not args: return usage()

    logging.basicConfig(format='%(asctime)s %(levelname)s %(message)s', level=level)

    for path in args:
        logging.info(f'Loading: {path!r}...')
        db = GraphDB(path)
        #logging.info(f'Running...')
        #builder.run()

        # list all the methods and number of its uses. (being called)
        for method in db.get_allmethods():
            for node in method:
                if not node.is_funcall(): continue
                for func in node.data.split():
                    if func not in funargs: continue
                    for (label,arg) in funargs[func]:
                        out = set()
                        visit(out, arg, node.inputs[arg], set())
                        print(label, sorted(out))
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
