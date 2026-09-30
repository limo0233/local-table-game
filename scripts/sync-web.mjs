import { cp, mkdir, rm } from 'node:fs/promises'
import { resolve } from 'node:path'

const source = resolve(process.env.WEB_DIST ?? 'web/dist')
const target = resolve('android/app/src/main/assets/web')
await rm(target, { recursive: true, force: true })
await mkdir(target, { recursive: true })
await cp(source, target, { recursive: true })
console.log(`Synced ${source} -> ${target}`)
