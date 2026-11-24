export interface DiffLine {
  kind: 'add' | 'del' | 'same'
  text: string
}

/** Small LCS-based line diff: lines only in `to` are "add", lines only in `from` are "del". */
export function diffLines(from: string, to: string): DiffLine[] {
  const a = from.split('\n')
  const b = to.split('\n')
  const lcs: number[][] = Array.from({ length: a.length + 1 }, () => new Array<number>(b.length + 1).fill(0))
  for (let i = a.length - 1; i >= 0; i--)
    for (let j = b.length - 1; j >= 0; j--)
      lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1])
  const out: DiffLine[] = []
  let i = 0
  let j = 0
  while (i < a.length || j < b.length) {
    if (i < a.length && j < b.length && a[i] === b[j]) {
      out.push({ kind: 'same', text: a[i] })
      i++
      j++
    } else if (j < b.length && (i === a.length || lcs[i][j + 1] >= lcs[i + 1][j])) {
      out.push({ kind: 'add', text: b[j++] })
    } else {
      out.push({ kind: 'del', text: a[i++] })
    }
  }
  return out
}

const PREFIX = { add: '+ ', del: '- ', same: '  ' }

/** Shows what changes going from the selected version to the current one; prefixes carry the meaning, not colour. */
export default function VersionDiff({ label, from, to }: { label: string; from: string; to: string }) {
  const lines = diffLines(from, to)
  return (
    <pre className="diff" role="region" aria-label={label}>
      {lines.map((l, i) => (
        <span key={i} className={`diff-${l.kind}`}>
          {PREFIX[l.kind]}
          {l.text}
          {'\n'}
        </span>
      ))}
    </pre>
  )
}
