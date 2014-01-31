
package main

import "fmt"
import "os"
import "strings"
import "regexp"
import "io/ioutil"

func main() {
  path := "/usr/share/dict/words"

  if len(os.Args) > 1 {
    path = os.Args[1]
  }

  rawWords, err := ioutil.ReadFile(path)
  if err != nil { panic(err) }

  rawContents, err := ioutil.ReadAll(os.Stdin)
  if err != nil { panic(err) }

  contents := string(rawContents)
  words := strings.Split(string(rawWords), "\n")

  wordsMap := make(map[string] bool, len(words))

  for i := 0; i < len(words); i++ {
    wordsMap[words[i]] = true
  }

  reg := regexp.MustCompile(`[^ \n]+`)

  contents = reg.ReplaceAllStringFunc(contents, func (s string) string {
    if wordsMap[strings.ToLower(s)] {
      return s
    } else {
      return fmt.Sprintf("<%s>", s)
    }
  })

  fmt.Printf(contents)
}
