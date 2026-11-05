import test from 'node:test'
import assert from 'node:assert/strict'
import { readSession, savedToken, saveToken, savedUsername, saveUsername } from './adminPreferences.js'

function storage() {
  const data = new Map()
  return { getItem: key => data.get(key) ?? null, setItem: (key,value) => data.set(key,value), removeItem: key => data.delete(key) }
}

test('expired and malformed saved sessions are ignored', () => {
  const saved = storage()
  saved.setItem('allez.admin-session', JSON.stringify({ token:'valid-token', expiresAt:2000 }))
  assert.equal(readSession(saved,1000),'valid-token')
  assert.equal(readSession(saved,2000),'')
  saved.setItem('allez.admin-session','{invalid')
  assert.equal(readSession(saved,1000),'')
})

test('persistent login survives a new tab, tab-only login does not; logout clears both', () => {
  const previous = globalThis.window
  const localStorage = storage()
  globalThis.window = { localStorage, sessionStorage:storage() }
  try {
    saveToken('persistent-token',true,2592000)
    saveUsername('admin')
    globalThis.window = { localStorage, sessionStorage:storage() }
    assert.equal(savedToken(),'persistent-token')
    assert.equal(savedUsername(),'admin')
    saveToken('')
    assert.equal(savedToken(),'')
    assert.equal(savedUsername(),'admin')
    saveToken('tab-token',false,28800)
    assert.equal(savedToken(),'tab-token')
    globalThis.window = { localStorage, sessionStorage:storage() }
    assert.equal(savedToken(),'')
    saveUsername('')
    assert.equal(savedUsername(),'')
  } finally { globalThis.window = previous }
})
