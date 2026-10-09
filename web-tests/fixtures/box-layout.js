// Compare independent surfaces, including panels separated by inserted wrappers.
// Containment (a panel inside its own section) is intentional and excluded.
// Profile header/body are adjoining regions of one panel, not independent boxes.
export function measureBoxLayout(root) {
  const selector='.panel,.stat,.stat-card,.inventory-card,.pro-record,.enterprise-section,.logistics-alerts,.logistics-overview,.stats,.stats-grid,.enterprise-kpis,.grid-2,.grid-equal,.notification-grid,.management-filters,.management-load,.calendar-strip,.environment-widget,.dashboard-tools,.dashboard-network,.access-layout,.overview-head';
  const surfaces=[...root.querySelectorAll(selector)].filter(node=>node.getClientRects().length);
  const issues=[];
  for(let i=0;i<surfaces.length;i++)for(let j=i+1;j<surfaces.length;j++) {
    const a=surfaces[i],b=surfaces[j];
    if(a.contains(b)||b.contains(a))continue;
    const ar=a.getBoundingClientRect(),br=b.getBoundingClientRect();
    if(!ar.width||!ar.height||!br.width||!br.height)continue;
    const overlapX=Math.min(ar.right,br.right)-Math.max(ar.left,br.left);
    const overlapY=Math.min(ar.bottom,br.bottom)-Math.max(ar.top,br.top);
    const gap=Math.max(br.top-ar.bottom,ar.top-br.bottom);
    const collision=overlapX>2&&overlapY>2;
    const touching=overlapX>Math.min(ar.width,br.width)*.5&&gap>=-1&&gap<12;
    if(collision||touching)issues.push({a:a.id||a.className,b:b.id||b.className,collision,gap:Math.round(gap*10)/10});
  }
  return {surfaces:surfaces.length,issues,overflow:document.documentElement.scrollWidth>innerWidth};
}
