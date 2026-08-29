export const SUPER_DOUBLE_ITEM_ID = 30020001

export function itemCount(session, itemId) {
  const item = session?.inventory?.find((value) => Number(value.itemId) === Number(itemId))
  return Math.max(0, Number(item?.count || 0))
}
