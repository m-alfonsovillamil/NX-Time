/** «4,2 %», o una raya si no hay cifra todavía: un «0,0 %» el día 1 afirmaría que nadie ha faltado. */
export function porcentaje(valor: number | null | undefined): string {
  if (valor === null || valor === undefined) return '—';
  return `${Number(valor).toLocaleString('es-ES', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} %`;
}
