grammar BF;

expr : content* EOF;
block : '[' content* ']' ;
content : block
        | value
        ;
value : LEFT
      | RIGHT
      | INC
      | DEC
      | OUT
      | IN
      ;
LEFT : '<' ;
RIGHT : '>' ;
INC : '+' ;
DEC : '-' ;
OUT : '.' ;
IN : ',' ;

ANY : . -> skip ;