const LINE_BREAK_RE = /[\r\n]+/g
const TARGET_COLUMN_NAME = '指标名称'

type DialogChartField = {
  dataeaseName?: string
  name?: string
  chartShowName?: string
}

const sanitizeCellValue = (value: unknown) => {
  if (typeof value !== 'string') {
    return value
  }

  return value.replace(LINE_BREAK_RE, '')
}

export const sanitizeDialogChartData = <
  T extends { tableRow?: Record<string, unknown>[]; fields?: DialogChartField[] }
>(
  chartDataInfo: T
): T => {
  if (!chartDataInfo?.tableRow?.length) {
    return chartDataInfo
  }

  const targetColumns =
    chartDataInfo.fields
      ?.filter(field => (field.chartShowName ?? field.name) === TARGET_COLUMN_NAME)
      .map(field => field.dataeaseName)
      .filter((field): field is string => Boolean(field)) ?? []

  if (!targetColumns.length) {
    return chartDataInfo
  }

  const targetColumnSet = new Set(targetColumns)
  let hasChanged = false

  const tableRow = chartDataInfo.tableRow.map(row => {
    let nextRow: Record<string, unknown> | null = null

    for (const key of targetColumnSet) {
      const value = row[key]
      const sanitizedValue = sanitizeCellValue(value)

      if (sanitizedValue === value) {
        continue
      }

      nextRow ||= { ...row }
      nextRow[key] = sanitizedValue
    }

    if (nextRow) {
      hasChanged = true
      return nextRow
    }

    return row
  })

  if (!hasChanged) {
    return chartDataInfo
  }

  return {
    ...chartDataInfo,
    tableRow
  }
}
