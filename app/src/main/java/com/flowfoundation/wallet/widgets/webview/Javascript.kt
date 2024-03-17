package com.flowfoundation.wallet.widgets.webview

import com.flowfoundation.wallet.widgets.webview.fcl.generateFclExtensionInject

// inject lilico auth login
val JS_FCL_EXTENSIONS = """
    if (!Array.isArray(window.fcl_extensions)) {
      window.fcl_extensions = []
    }
    window.fcl_extensions.push(${generateFclExtensionInject()})
""".trimIndent()

val JS_LISTEN_WINDOW_FCL_MESSAGE = """
    window.addEventListener('message', function (event) {
      window.android.message(JSON.stringify(event.data))
    })
""".trimIndent()

val JS_LISTEN_FLOW_WALLET_TRANSACTION = """
    window.addEventListener('FLOW::TX', function (event) {
      window.android.transaction(JSON.stringify({type: 'FLOW::TX', ...event.detail}))
    })
""".trimIndent()

val JS_QUERY_WINDOW_COLOR = """
    function getBodyColor() {
      const getCbgcolor = (elem) => {
        if (!getCbgcolor.top)
          getCbgcolor.top = (() => {
            try {
              return window.top.document.documentElement;
            } catch (e) {
              return null; /* CORS */
            }
          })();
    
        while (true) {
          let cbg = window.getComputedStyle(elem).getPropertyValue("background-color");
          if (cbg && cbg != "rgba(0, 0, 0, 0)" && cbg != "transparent") return cbg;
          if (elem === getCbgcolor.top) return cbg;
          elem = elem.parentElement;
          if (!elem) return "";
        }
      };
    
      const rgb2Hex = (s) => s.match(/[0-9]+/g).reduce((a, b) => a + (b | 256).toString(16).slice(1), "#");
      return rgb2Hex(getCbgcolor(document.body)).substring(0, 7);
    }
    
    window.android.windowColor(getBodyColor());
""".trimIndent()