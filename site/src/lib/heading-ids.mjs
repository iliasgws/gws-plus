const slugify = (text) =>
  text
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');

const walk = (node, fn) => {
  fn(node);
  for (const child of node.children ?? []) walk(child, fn);
};

export default function headingIds() {
  return (tree) => {
    walk(tree, (node) => {
      if (/^h[1-6]$/.test(node.tagName) && !node.properties?.id) {
        const text = (node.children ?? [])
          .map((child) => (child.type === 'text' ? child.value : ''))
          .join('');
        if (text.trim()) node.properties = { ...node.properties, id: slugify(text) };
      }
    });
  };
}
