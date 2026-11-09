import test from 'node:test'
import assert from 'node:assert/strict'
import { isStandalone, mobilePlatform } from './homeScreenSupport.js'

test('recognizes mobile devices including iPad desktop mode without treating a touch PC as mobile', () => {
  assert.equal(mobilePlatform({ userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)' }), 'ios')
  assert.equal(mobilePlatform({ userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15)', maxTouchPoints: 5 }), 'ios')
  assert.equal(mobilePlatform({ userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15)', maxTouchPoints: 0 }), null)
  assert.equal(mobilePlatform({ userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', maxTouchPoints: 10 }), null)
  assert.equal(mobilePlatform({ userAgent: 'Mozilla/5.0 (Linux; Android 15)' }), 'android')
  assert.equal(mobilePlatform({ userAgent: '', userAgentData: { mobile: true } }), 'mobile')
})

test('recognizes iOS and manifest standalone execution without assuming ordinary browsers are installed', () => {
  assert.equal(isStandalone({ standalone: true }, false), true)
  assert.equal(isStandalone({}, true), true)
  assert.equal(isStandalone({ standalone: false }, false), false)
})
