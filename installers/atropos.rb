class Atropos < Formula
  desc "Deterministic software engineering engine"
  homepage "https://github.com/mjmichaelware/ATROPOS"
  version "0.1.0"
  url "file://build/libs/ATROPOS.jar"
  def install
    libexec.install "ATROPOS.jar"
    (bin/"atropos").write <<~SH
      #!/bin/sh
      exec java -jar #{libexec}/ATROPOS.jar "\build/libs/ATROPOS.jar build/installers"
    SH
  end
end
