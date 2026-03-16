package meldexun.betterconfig;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Iterator;

class ConfigWriter implements AutoCloseable {

	private final BufferedWriter writer;
	private int indentation;
	private boolean lineStarted;

	ConfigWriter(BufferedWriter writer) {
		this.writer = writer;
	}

	<T> ConfigWriter write(Iterable<T> iterable, ThrowingBiPredicate<ConfigWriter, T, IOException> elementWriter) throws IOException {
		return this.write(iterable, elementWriter, ConfigWriter::newLine);
	}

	<T> ConfigWriter write(Iterable<T> iterable, ThrowingBiPredicate<ConfigWriter, T, IOException> elementWriter, ThrowingConsumer<ConfigWriter, IOException> separatorWriter) throws IOException {
		Iterator<T> iterator = iterable.iterator();
		while (iterator.hasNext()) {
			if (elementWriter.test(this, iterator.next()) && iterator.hasNext()) {
				separatorWriter.accept(this);
			}
		}
		return this;
	}

	ConfigWriter writeLine(char c) throws IOException {
		return this.write(c).newLine();
	}

	ConfigWriter writeLine(char c, int count) throws IOException {
		return this.write(c, count).newLine();
	}

	ConfigWriter writeLine(String s) throws IOException {
		return this.write(s).newLine();
	}

	ConfigWriter writeCommentLine(String s) throws IOException {
		return this.startComment().writeLine(s);
	}

	ConfigWriter startComment() throws IOException {
		return this.write("# ");
	}

	ConfigWriter write(char c) throws IOException {
		this.indent();
		this.writer.write(c);
		return this;
	}

	ConfigWriter write(char c, int count) throws IOException {
		this.indent();
		for (int i = 0; i < count; i++) {
			this.writer.write(c);
		}
		return this;
	}

	ConfigWriter write(String s) throws IOException {
		this.indent();
		this.writer.write(s);
		return this;
	}

	private void indent() throws IOException {
		if (!this.lineStarted) {
			for (int i = 0; i < this.indentation * 4; i++) {
				this.writer.write(' ');
			}
			this.lineStarted = true;
		}
	}

	ConfigWriter newLine() throws IOException {
		this.writer.newLine();
		this.lineStarted = false;
		return this;
	}

	ConfigWriter incrementIndentation() {
		this.indentation++;
		return this;
	}

	ConfigWriter decrementIndentation() {
		this.indentation--;
		return this;
	}

	@Override
	public void close() throws IOException {
		this.writer.close();
	}

}
