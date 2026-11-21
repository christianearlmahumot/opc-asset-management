import { formatMoney } from '../../shared/format'

const issued = (d) =>
  d ? new Date(`${d}T00:00:00`).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : ''
const invoiceDate = (d) =>
  d ? new Date(`${d}T00:00:00`).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: '2-digit' }).replace(/ /g, '-') : ''

/**
 * The issue order as it will print (UI-202 right panel, UI-204 view). Same fields and order as the
 * OPC's paper form; the PDF from the server is the copy that is actually printed and signed.
 */
export default function IoSheet({ io, lines }) {
  const total = lines.reduce((s, l) => s + (Number(l.totalCost) || 0), 0)
  return (
    <div className="io-sheet">
      <div className="io-center io-inst">CEBU INSTITUTE OF TECHNOLOGY – UNIVERSITY</div>
      <div className="io-center io-title">ISSUE ORDER</div>
      <div className="io-top">
        <div>
          <div>Issued by: {io.issuedByName ?? '—'}</div>
          <div>Purchased from: {io.purchasedFrom ?? '—'}</div>
          <div>Invoice date: {invoiceDate(io.invoiceDate) || '—'} · Inv. No.: {io.invoiceNo ?? '—'}</div>
        </div>
        <div className="io-no">
          <strong>No. {io.ioNo ?? '———————'}</strong>
          <div>{issued(io.issuedOn)}</div>
          <strong>{io.departmentCode}</strong>
          {io.room && <div>{io.room}</div>}
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th style={{ width: 70 }}>QTY</th>
            <th>ARTICLES / DESCRIPTION</th>
            <th className="r">UNIT COST</th>
            <th className="r">TOTAL</th>
          </tr>
        </thead>
        <tbody>
          {lines.map((l, i) => (
            <tr key={l.id ?? i}>
              <td className="num">{l.quantity} {l.unit}</td>
              <td>{l.description}</td>
              <td className="r num">{formatMoney(l.unitCost)}</td>
              <td className="r num">{formatMoney(l.totalCost)}</td>
            </tr>
          ))}
          <tr className="io-total">
            <td colSpan={3} />
            <td className="r num">{formatMoney(total)}</td>
          </tr>
        </tbody>
      </table>
      {io.remarks && <div style={{ marginTop: 6 }}>Remarks: {io.remarks}</div>}
      <div className="io-signs">
        <div>Approved by:<strong>{io.approvedByName ?? ' '}</strong>Property Custodian</div>
        <div>Noted by:<strong>{io.notedByName ?? ' '}</strong>{io.notedByTitle ?? 'Dean'}</div>
        <div>Received by:<strong>{io.receivedByName ?? io.accountable ?? ' '}</strong>{io.receivedByTitle ?? ''}</div>
      </div>
    </div>
  )
}
