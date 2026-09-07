package wiki.kivo.core.content

import java.util.Base64

/** 正文只作为 UTF-8 数据进入独立图表文档，不拼接进 HTML 或 JavaScript 字面量。 */
internal object MermaidDocument {
    fun html(source: String, dark: Boolean, expanded: Boolean): String {
        val encoded = Base64.getEncoder().encodeToString(source.toByteArray(Charsets.UTF_8))
        return """<!doctype html><html lang="zh-CN"><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=5">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'unsafe-inline'; img-src 'none'; connect-src 'none'; font-src 'none'; base-uri 'none'; form-action 'none'">
<style>body{margin:0;padding:12px;font-family:sans-serif;background:${if (dark) "#17202b" else "#ffffff"};color:${if (dark) "#edf3fa" else "#1d2937"}}#diagram{${if (expanded) "min-width:700px;" else ""}}svg{display:block;max-width:100%;height:auto;margin:auto}#status{font-size:14px}</style>
</head><body><p id="status" role="status">正在绘制图表…</p><div id="diagram" role="img" aria-label="Mermaid 图表"></div>
<script src="mermaid.min.js"></script><script>
window.kivoDiagramState='loading';
(async()=>{try{
const source=new TextDecoder().decode(Uint8Array.from(atob('$encoded'),c=>c.charCodeAt(0)));
if(source.length>32000)throw new Error('图表超过绘制预算，请查看源码');
mermaid.initialize({startOnLoad:false,securityLevel:'strict',maxTextSize:32000,maxEdges:300,
theme:'${if (dark) "dark" else "default"}',htmlLabels:false,flowchart:{htmlLabels:false},
secure:['secure','securityLevel','startOnLoad','maxTextSize','maxEdges','htmlLabels','flowchart','themeCSS','fontFamily']});
const result=await mermaid.render('kivo-graph',source);
document.getElementById('diagram').innerHTML=result.svg;
document.getElementById('status').textContent='';window.kivoDiagramState='ready';
}catch(error){document.getElementById('diagram').textContent='';document.getElementById('status').textContent='图表暂时无法绘制，可展开源码查看。';window.kivoDiagramState='error';}})();
</script></body></html>"""
    }
}
