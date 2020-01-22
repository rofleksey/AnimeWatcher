package ru.rofleksey.animewatcher.api.unpackers

import com.squareup.duktape.Duktape

class PACKERUnpacker {
    companion object {
        val CODE = """
            var Packer = {
              detect: function(str) {
                return (Packer.get_chunks(str).length > 0);
              },

              get_chunks: function(str) {
                var chunks = str.match(/eval\(\(?function\(.*?(,0,\{\}\)\)|split\('\|'\)\)\))(${'$'}|\n)/g);
                return chunks ? chunks : [];
              },

              unpack: function(str) {
                var chunks = Packer.get_chunks(str),
                  chunk;
                for (var i = 0; i < chunks.length; i++) {
                  chunk = chunks[i].replace(/\n${'$'}/, '');
                  str = str.split(chunk).join(Packer.unpack_chunk(chunk));
                }
                return str;
              },

              unpack_chunk: function(str) {
                var unpacked_source = '';
                var __eval = eval;
                if (Packer.detect(str)) {
                  try {
                    eval = function(s) { // jshint ignore:line
                      unpacked_source += s;
                      return unpacked_source;
                    }; // jshint ignore:line
                    __eval(str);
                    if (typeof unpacked_source === 'string' && unpacked_source) {
                      str = unpacked_source;
                    }
                  } catch (e) {
                    // well, it failed. we'll just return the original, instead of crashing on user.
                  }
                }
                eval = __eval; // jshint ignore:line
                return str;
              }
            };
        """.trimIndent()

        interface Packer {
            fun unpack(s: String): String
        }

        fun unpack(what: String): String {
            return Duktape.create().use {
                it.evaluate(CODE)
                val printer = it.get("Packer", Packer::class.java)
                printer.unpack(what)
            }
        }
    }
}