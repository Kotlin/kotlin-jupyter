import ast
import astor
import sys


class TypeHintRemover(ast.NodeTransformer):

    def visit_FunctionDef(self, node):
        # remove the return type definition
        node.returns = None
        # remove all argument annotations
        if node.args.args:
            for arg in node.args.args:
                arg.annotation = None
        self.generic_visit(node)
        return node

    def visit_AnnAssign(self, node):
        if node.value is None:
            return None
        return ast.Assign([node.target], node.value)

    def visit_Import(self, node):
        node.names = [n for n in node.names if n.name != 'typing']
        return node if node.names else None

    def visit_ImportFrom(self, node):
        return node if node.module != 'typing' else None


def remove_type_hints(source: str):
    # parse the source code into an AST
    parsed_source = ast.parse(source)
    # remove all type annotations, function return type definitions
    # and import statements from 'typing'
    transformed = TypeHintRemover().visit(parsed_source)
    # convert the AST back to source code
    return astor.to_source(transformed)


def main():
    _, source_name, dest_name = sys.argv
    with open(source_name, "r") as sourceFile:
        source = "\n".join(sourceFile.readlines())
        dest = remove_type_hints(source)
        with open(dest_name, "w") as destFile:
            destFile.write(dest)


if __name__ == "__main__":
    main()
