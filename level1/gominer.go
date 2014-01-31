package main

import (
  "crypto/sha1"
  "fmt"
  "io"
  "io/ioutil"
  "os"
  "strings"
  "math/big"
  "runtime"
  "math/rand"
  "time"
)

var NCPU = runtime.NumCPU()

type Found struct {
  sha1, commit string
}

func Unpack(f Found) (string, string) {
    return f.sha1, f.commit
}

func main() {

  rand.Seed(time.Now().UnixNano())

  runtime.GOMAXPROCS(NCPU)

  difficulty := strings.Trim(os.Args[1], " \n")

  rawCommit, err := ioutil.ReadAll(os.Stdin)
  if err != nil { panic(err) }

  commit := string(rawCommit)
  ch := make(chan Found)

  for i := 0; i < NCPU; i++ {
    random := rand.Int63()
    go mine(random, commit, difficulty, ch)
  }

  sha, commit := Unpack(<- ch)

  ioutil.WriteFile("gominer.txt", []byte(commit), 0777)

  fmt.Printf("%s", sha)
}

func mine(random int64, commit string, difficulty string, ch chan Found) {
  sha := ""
  commit0 := ""
  counter := big.NewInt(0)
  one := big.NewInt(1)

  for {
    counter.Add(counter, one)
    commit0 = fmt.Sprintf("%s\n\n%x-%x", commit, random, counter)
    sha = gitSha1(commit0)

    if sha < difficulty {
      break
    }
  }
  ch <- Found { sha, commit0 }
}

func gitSha1(commit string) string {
  blob := fmt.Sprintf("commit %d\x00%s", len(commit), commit)

  h := sha1.New()
  io.WriteString(h, blob)

  return fmt.Sprintf("%x", h.Sum(nil))
}
