import { distributionPercent } from './summaryPresentation'

describe('impact summary presentation', () => {
  it('calculates bounded distribution percentages', () => {
    expect(distributionPercent(3, 12)).toBe(25)
    expect(distributionPercent(1, 0)).toBe(0)
    expect(distributionPercent(14, 12)).toBe(100)
  })
})
