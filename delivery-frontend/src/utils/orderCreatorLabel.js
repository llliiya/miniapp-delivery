export function formatOrderCreatorLabel(order) {
  if (order?.createdBySource === 'courier_service') {
    return 'Создан оператором курьерской службы'
  }
  if (order?.createdBySource === 'restaurant') {
    return 'Создан сотрудником ресторана'
  }
  return null
}
