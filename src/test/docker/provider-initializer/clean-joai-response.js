#!/usr/bin/node

const jsdom = require('jsdom')
const { JSDOM } = jsdom
const chunks = [];

process.stdin.on('readable', () => {
    let chunk

    while (null !== (chunk = process.stdin.read())) {
        chunks.push(chunk)
    }
}).on('end', () => {
    const dom = new JSDOM(chunks.join(''))

    // Assume the useful data is in <table id="form">
    const table = dom.window.document.querySelector('table#form')

    if (table !== null) {
        console.log(htmlTableToPlaintext(table))
    }
})

/**
 * Transforms an HTML table to a plaintext representation.
 *
 * @param {HTMLTableElement} table A table
 * @returns A string representing the table
 */
 function htmlTableToPlaintext(table) {
    return Array.from(table.querySelectorAll('tr')).map(tr => {
        return Array.from(tr.querySelectorAll('td')).map(td => {
            // Strip leading and trailing whitespace, and collapse other whitespace
            return td.textContent.replaceAll(/^\s+/g, '').replaceAll(/\s+$/g, '').replaceAll(/\s+/g, ' ')
        }).join('\t')
    }).join('\n')
}
